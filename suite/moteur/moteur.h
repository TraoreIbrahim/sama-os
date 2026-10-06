// Moteur des applications bureautiques de Sama (Sama Sheet, puis Sama Docs) : le moteur de LibreOffice, piloté par
// LibreOfficeKit. Un seul fil (« moteur ») appelle LibreOfficeKit ; l'interface ne se fige donc jamais pendant un
// chargement, un calcul ou le dessin des tuiles.
#pragma once

#define LOK_USE_UNSTABLE_API
#include <LibreOfficeKit/LibreOfficeKitInit.h>
#include <LibreOfficeKit/LibreOfficeKit.h>
#include <LibreOfficeKit/LibreOfficeKitEnums.h>

#include <QObject>
#include <QSet>
#include <QThread>
#include <functional>

class Moteur : public QObject
{
    Q_OBJECT
public:
    // Le moteur du programme (créé au premier appel, avec son fil)
    static Moteur *instance();

    // Exécute f dans le fil du moteur (dans l'ordre des demandes). Le premier appel démarre LibreOffice.
    void executer(std::function<void()> f);
    // Idem, mais attend la fin (destruction d'un document)
    void executerEtAttendre(std::function<void()> f);

    // À n'utiliser que dans le fil du moteur
    LibreOfficeKit *lok();
    QString erreur() const { return m_erreur; }
    // Documents ouverts (fermés proprement à la fin du programme)
    void ajouter(LibreOfficeKitDocument *d);
    void retirer(LibreOfficeKitDocument *d);

private:
    Moteur();
    QThread m_fil;
    QObject *m_contexte = nullptr;
    LibreOfficeKit *m_lok = nullptr;
    bool m_essaye = false;
    QString m_erreur;
    QSet<LibreOfficeKitDocument *> m_documents;
};
