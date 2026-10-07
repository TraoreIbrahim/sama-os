#include "moteur.h"

#include <QCoreApplication>
#include <QDir>
#include <QFile>
#include <QRegularExpression>
#include <QSemaphore>
#include <QStandardPaths>
#include <QUrl>

#include <array>
#include <cstdlib>
#include <pwd.h>
#include <unistd.h>

namespace {
// LibreOffice de Debian ; SAMA_LIBREOFFICE permet d'en essayer un autre
QByteArray dossierLibreOffice()
{
    const QByteArray d = qgetenv("SAMA_LIBREOFFICE");
    return d.isEmpty() ? QByteArrayLiteral("/usr/lib/libreoffice/program") : d;
}

// Réglages du moteur, écrits dans son profil avant le démarrage : monnaie par défaut, le franc CFA (XOF) — le bouton
// « FCFA » de Sama Sheet applique la monnaie par défaut ; barre d'état de la sélection ; traits de la grille doux, pas de
// vérification d'orthographe pendant la saisie (comme Excel) ; couleurs des graphiques (forêt, ocre, bleu, latérite…)
const char *const REGLAGES[][3] = {
    {"/org.openoffice.Setup/L10N", "ooSetupCurrency", "XOF-fr-CI"},
    {"/org.openoffice.Office.Calc/Layout/Other", "StatusbarMultiFunction", "522"},   // somme, moyenne, nombre de valeurs
    // jeu de couleurs de Sama (voir NOEUDS)
    {"/org.openoffice.Office.UI/ColorScheme", "CurrentColorScheme", "Sama"},
    {"/org.openoffice.Office.Linguistic/SpellChecking", "IsSpellAuto", "false"},     // pas de soulignés rouges dans les cases
    {"/org.openoffice.Office.Chart/DefaultColor", "Series",
     "3107671 14259770 4091550 11883311 9286778 8085132 14926443 5151386 10251086 13208219 7244968 9077367"},
};

// Éléments ajoutés à des ensembles : le jeu de couleurs « Sama » (celui du moteur, « automatique », ne se change pas),
// avec les traits de la grille très légers (#EFEFEE, ceux des maquettes), et sans les pointillés bleus là où des lignes
// ou des colonnes sont masquées (un filtre en masque : Excel ne les montre pas, l'« automatique » non plus) ; le reste
// garde les couleurs par défaut.
const char *const NOEUDS[][3] = {
    {"/org.openoffice.Office.UI/ColorScheme/ColorSchemes", "Sama",
     "<node oor:name=\"CalcGrid\"><prop oor:name=\"Light\"><value>15724526</value></prop></node>"
     "<node oor:name=\"CalcHiddenColRow\"><prop oor:name=\"IsVisible\"><value>false</value></prop></node>"},
};

// Macros de Sama (tableaux…) : les modules Basic de SAMA_MOTEUR_BASIC (par défaut /usr/lib/samaos/moteur/basic) sont
// copiés dans la bibliothèque Standard du profil, et inscrits dans sa liste. Les fichiers de la bibliothèque sont écrits
// s'ils manquent (profil neuf : le moteur ne les recopie pas si le dossier existe déjà).
void ecrireSiAbsent(const QString &chemin, const QByteArray &contenu)
{
    QFile f(chemin);
    if (f.exists()) return;
    if (f.open(QIODevice::WriteOnly)) f.write(contenu);
}

void installerMacros(const QString &dossier)
{
    const QByteArray source = qgetenv("SAMA_MOTEUR_BASIC");
    const QDir modules(source.isEmpty() ? QStringLiteral("/usr/lib/samaos/moteur/basic") : QString::fromLocal8Bit(source));
    const QStringList fichiers = modules.entryList({QStringLiteral("*.xba")}, QDir::Files, QDir::Name);
    if (fichiers.isEmpty()) return;
    const QString basic = dossier + QStringLiteral("/user/basic"), standard = basic + QStringLiteral("/Standard");
    QDir().mkpath(standard);
    const QByteArray entete = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
    const QByteArray conteneur = entete
        + "<!DOCTYPE library:libraries PUBLIC \"-//OpenOffice.org//DTD OfficeDocument 1.0//EN\" \"libraries.dtd\">\n"
          "<library:libraries xmlns:library=\"http://openoffice.org/2000/library\" xmlns:xlink=\"http://www.w3.org/1999/xlink\">\n"
          " <library:library library:name=\"Standard\" library:link=\"false\"/>\n</library:libraries>\n";
    ecrireSiAbsent(basic + QStringLiteral("/script.xlc"), conteneur);
    ecrireSiAbsent(basic + QStringLiteral("/dialog.xlc"), conteneur);
    const QByteArray bibliotheque = entete
        + "<!DOCTYPE library:library PUBLIC \"-//OpenOffice.org//DTD OfficeDocument 1.0//EN\" \"library.dtd\">\n"
          "<library:library xmlns:library=\"http://openoffice.org/2000/library\" library:name=\"Standard\" "
          "library:readonly=\"false\" library:passwordprotected=\"false\"";
    ecrireSiAbsent(standard + QStringLiteral("/dialog.xlb"), bibliotheque + "/>\n");
    ecrireSiAbsent(standard + QStringLiteral("/script.xlb"), bibliotheque + ">\n</library:library>\n");
    QFile liste(standard + QStringLiteral("/script.xlb"));
    if (!liste.open(QIODevice::ReadOnly)) return;
    QString texte = QString::fromUtf8(liste.readAll());
    liste.close();
    for (const QString &fichier : fichiers) {
        const QString nom = fichier.chopped(4);
        QFile::remove(standard + QLatin1Char('/') + fichier);
        QFile::copy(modules.filePath(fichier), standard + QLatin1Char('/') + fichier);
        const QString element = QStringLiteral("<library:element library:name=\"%1\"/>").arg(nom);
        if (!texte.contains(element)) texte.replace(QStringLiteral("</library:library>"), QStringLiteral(" ") + element + QStringLiteral("\n</library:library>"));
    }
    if (liste.open(QIODevice::WriteOnly | QIODevice::Truncate)) liste.write(texte.toUtf8());
}

// Le nom de la personne (le nom complet du compte, sinon son identifiant) : le moteur en signe ses commentaires (le
// profil de LibreOffice, « Données d'identité »)
QString nomPersonne()
{
    if (const passwd *p = getpwuid(getuid())) {
        const QString complet = QString::fromLocal8Bit(p->pw_gecos).section(QLatin1Char(','), 0, 0).trimmed();
        if (!complet.isEmpty()) return complet;
        return QString::fromLocal8Bit(p->pw_name);
    }
    return QString::fromLocal8Bit(qgetenv("USER"));
}

void preparerProfil(const QString &dossier)
{
    installerMacros(dossier);
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
    QList<std::array<QString, 3>> reglages;
    for (const auto &r : REGLAGES) reglages.append({QString::fromLatin1(r[0]), QString::fromLatin1(r[1]), QString::fromLatin1(r[2])});
    reglages.append({QStringLiteral("/org.openoffice.UserProfile/Data"), QStringLiteral("givenname"), nomPersonne().toHtmlEscaped()});
    reglages.append({QStringLiteral("/org.openoffice.UserProfile/Data"), QStringLiteral("sn"), QString()});
    for (const auto &r : reglages) {
        const QString &chemin = r[0], &nom = r[1], &valeur = r[2];
        const QString element = QStringLiteral("<item oor:path=\"%1\"><prop oor:name=\"%2\" oor:op=\"fuse\"><value>%3</value></prop></item>")
                                    .arg(chemin, nom, valeur);
        const QRegularExpression existant(QStringLiteral("<item oor:path=\"%1\"><prop oor:name=\"%2\"[^<]*>.*?</item>")
                                              .arg(QRegularExpression::escape(chemin), QRegularExpression::escape(nom)));
        if (texte.contains(existant))
            texte.replace(existant, element);
        else
            texte.replace(QStringLiteral("</oor:items>"), element + QStringLiteral("\n</oor:items>"));
    }
    for (const auto &n : NOEUDS) {
        const QString chemin = QString::fromLatin1(n[0]), nom = QString::fromLatin1(n[1]);
        const QString element = QStringLiteral("<item oor:path=\"%1\"><node oor:name=\"%2\" oor:op=\"replace\">%3</node></item>")
                                    .arg(chemin, nom, QString::fromLatin1(n[2]));
        const QRegularExpression existant(QStringLiteral("<item oor:path=\"%1\"><node oor:name=\"%2\"[^>]*>.*?</item>")
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
