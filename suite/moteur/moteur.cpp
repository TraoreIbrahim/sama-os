#include "moteur.h"

#include <QCoreApplication>
#include <QDir>
#include <QSemaphore>
#include <QStandardPaths>
#include <QUrl>

namespace {
// LibreOffice de Debian ; SAMA_LIBREOFFICE permet d'en essayer un autre
QByteArray dossierLibreOffice()
{
    const QByteArray d = qgetenv("SAMA_LIBREOFFICE");
    return d.isEmpty() ? QByteArrayLiteral("/usr/lib/libreoffice/program") : d;
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
    // À la fermeture du programme, le fil s'arrête proprement
    QObject::connect(qApp, &QCoreApplication::aboutToQuit, qApp, [this] {
        executerEtAttendre([this] {
            if (m_lok)
                m_lok->pClass->destroy(m_lok);
            m_lok = nullptr;
        });
        m_fil.quit();
        m_fil.wait(3000);
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
        QDir().mkpath(profil);
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
