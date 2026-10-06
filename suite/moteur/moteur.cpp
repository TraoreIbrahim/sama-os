#include "moteur.h"

#include <QCoreApplication>
#include <QDir>
#include <QFile>
#include <QRegularExpression>
#include <QSemaphore>
#include <QStandardPaths>
#include <QUrl>

#include <cstdlib>

namespace {
// LibreOffice de Debian ; SAMA_LIBREOFFICE permet d'en essayer un autre
QByteArray dossierLibreOffice()
{
    const QByteArray d = qgetenv("SAMA_LIBREOFFICE");
    return d.isEmpty() ? QByteArrayLiteral("/usr/lib/libreoffice/program") : d;
}

// Réglages du moteur, écrits dans son profil avant le démarrage : monnaie par défaut, le franc CFA (XOF) — le bouton
// « FCFA » de Sama Sheet applique la monnaie par défaut ; couleurs des graphiques (forêt, ocre, bleu, latérite…)
const char *const REGLAGES[][3] = {
    {"/org.openoffice.Setup/L10N", "ooSetupCurrency", "XOF-fr-CI"},
    {"/org.openoffice.Office.Chart/DefaultColor", "Series",
     "3107671 14259770 4091550 11883311 9286778 8085132 14926443 5151386 10251086 13208219 7244968 9077367"},
};

void preparerProfil(const QString &dossier)
{
    QDir().mkpath(dossier + QStringLiteral("/user"));
    QFile f(dossier + QStringLiteral("/user/registrymodifications.xcu"));
    QString texte;
    if (f.open(QIODevice::ReadOnly)) {
        texte = QString::fromUtf8(f.readAll());
        f.close();
    }
    if (texte.isEmpty())
        texte = QStringLiteral("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                               "<oor:items xmlns:oor=\"http://openoffice.org/2001/registry\" "
                               "xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" "
                               "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n</oor:items>\n");
    for (const auto &r : REGLAGES) {
        const QString chemin = QString::fromLatin1(r[0]), nom = QString::fromLatin1(r[1]), valeur = QString::fromLatin1(r[2]);
        const QString element = QStringLiteral("<item oor:path=\"%1\"><prop oor:name=\"%2\" oor:op=\"fuse\"><value>%3</value></prop></item>")
                                    .arg(chemin, nom, valeur);
        const QRegularExpression existant(QStringLiteral("<item oor:path=\"%1\"><prop oor:name=\"%2\"[^<]*>.*?</item>")
                                              .arg(QRegularExpression::escape(chemin), QRegularExpression::escape(nom)));
        if (texte.contains(existant))
            texte.replace(existant, element);
        else
            texte.replace(QStringLiteral("</oor:items>"), element + QStringLiteral("\n</oor:items>"));
    }
    if (f.open(QIODevice::WriteOnly | QIODevice::Truncate))
        f.write(texte.toUtf8());
}
}

Moteur *Moteur::instance()
{
    static Moteur *moteur = new Moteur();
    return moteur;
}

Moteur::Moteur()
{
    m_fil.setObjectName(QStringLiteral("moteur"));
    m_fil.start();
    m_contexte = new QObject();
    m_contexte->moveToThread(&m_fil);
    // Langues de la vérification (sans cette liste, LibreOfficeKit les coupe toutes)
    if (qEnvironmentVariableIsEmpty("LOK_ALLOWLIST_LANGUAGES"))
        qputenv("LOK_ALLOWLIST_LANGUAGES", "fr_FR en_US en_GB");
    // À la fermeture : les documents sont fermés (leurs fichiers de verrou retirés), puis le programme s'arrête net.
    // LibreOffice plante dans ses destructeurs de fin de programme : on ne les laisse pas tourner.
    QObject::connect(qApp, &QCoreApplication::aboutToQuit, qApp, [this] {
        executerEtAttendre([this] {
            for (LibreOfficeKitDocument *d : std::as_const(m_documents)) {
                d->pClass->registerCallback(d, nullptr, nullptr);
                d->pClass->destroy(d);
            }
            m_documents.clear();
        });
        std::_Exit(0);
    });
}

void Moteur::executer(std::function<void()> f)
{
    QMetaObject::invokeMethod(m_contexte, std::move(f), Qt::QueuedConnection);
}

void Moteur::executerEtAttendre(std::function<void()> f)
{
    if (QThread::currentThread() == &m_fil) {
        f();
        return;
    }
    QSemaphore fini;
    QMetaObject::invokeMethod(m_contexte, [&] { f(); fini.release(); }, Qt::QueuedConnection);
    fini.acquire();
}

LibreOfficeKit *Moteur::lok()
{
    if (!m_essaye) {
        m_essaye = true;
        // Profil à part : celui de LibreOffice reste libre s'il tourne en même temps
        const QString profil = QStandardPaths::writableLocation(QStandardPaths::GenericConfigLocation)
                               + QStringLiteral("/samaos/moteur-lo");
        preparerProfil(profil);
        const QByteArray url = QUrl::fromLocalFile(profil).toEncoded();
        m_lok = lok_init_2(dossierLibreOffice().constData(), url.constData());
        if (!m_lok)
            m_erreur = QStringLiteral("Le moteur de LibreOffice n'a pas pu démarrer.");
        else
            // (pas de LOK_FEATURE_DOCUMENT_PASSWORD : sans réponse, l'ouverture d'un document protégé attendrait
            // sans fin ; elle échoue donc, avec un message)
            m_lok->pClass->setOptionalFeatures(m_lok, LOK_FEATURE_PART_IN_INVALIDATION_CALLBACK
                                                      | LOK_FEATURE_NO_TILED_ANNOTATIONS);
    }
    return m_lok;
}

void Moteur::ajouter(LibreOfficeKitDocument *d)
{
    m_documents.insert(d);
}

void Moteur::retirer(LibreOfficeKitDocument *d)
{
    m_documents.remove(d);
}
