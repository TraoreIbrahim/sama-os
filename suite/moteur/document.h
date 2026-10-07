// DocumentLO : un document du moteur de LibreOffice, affiché dans une interface QML de Sama.
// Le moteur dessine le contenu (cellules, texte) par tuiles ; l'interface dessine le reste (en-têtes, curseur,
// sélection, barres d'outils) à partir des propriétés publiées ici. Coordonnées : pixels logiques du document
// (zoom compris) ; vueX/vueY = coin haut gauche de la partie visible.
#pragma once

#include <QHash>
#include <QImage>
#include <QPointer>
#include <QQueue>
#include <QQuickItem>
#include <QRectF>
#include <QSet>
#include <QVariantMap>

struct _LibreOfficeKitDocument;
class QSGTexture;

// Reçoit les annonces du moteur (dans le fil de LibreOffice) et les passe à l'interface
class Relais : public QObject
{
    Q_OBJECT
signals:
    void annonce(int type, const QByteArray &charge);
};

class DocumentLO : public QQuickItem
{
    Q_OBJECT
    Q_PROPERTY(QString chemin READ chemin NOTIFY cheminChanged)
    Q_PROPERTY(int etat READ etat NOTIFY etatChanged)
    Q_PROPERTY(QString erreur READ erreur NOTIFY etatChanged)
    Q_PROPERTY(int typeDocument READ typeDocument NOTIFY etatChanged)
    Q_PROPERTY(qreal zoom READ zoom WRITE setZoom NOTIFY zoomChanged)
    Q_PROPERTY(qreal vueX READ vueX WRITE setVueX NOTIFY vueChanged)
    Q_PROPERTY(qreal vueY READ vueY WRITE setVueY NOTIFY vueChanged)
    Q_PROPERTY(qreal largeurDocument READ largeurDocument NOTIFY tailleChanged)
    Q_PROPERTY(qreal hauteurDocument READ hauteurDocument NOTIFY tailleChanged)
    Q_PROPERTY(int partie READ partie NOTIFY partiesChanged)
    Q_PROPERTY(QStringList nomsParties READ nomsParties NOTIFY partiesChanged)
    Q_PROPERTY(QRectF curseur READ curseur NOTIFY curseurChanged)
    Q_PROPERTY(int colonne READ colonne NOTIFY curseurChanged)
    Q_PROPERTY(int ligne READ ligne NOTIFY curseurChanged)
    Q_PROPERTY(QString adresse READ adresse NOTIFY adresseChanged)
    Q_PROPERTY(QString formule READ formule NOTIFY formuleChanged)
    Q_PROPERTY(QVariantList selection READ selection NOTIFY selectionChanged)
    Q_PROPERTY(QRectF zoneSelection READ zoneSelection NOTIFY selectionChanged)
    Q_PROPERTY(QRectF curseurTexte READ curseurTexte NOTIFY curseurTexteChanged)
    Q_PROPERTY(bool curseurTexteVisible READ curseurTexteVisible NOTIFY curseurTexteChanged)
    Q_PROPERTY(QVariantMap etats READ etats NOTIFY etatsChanged)
    Q_PROPERTY(bool modifie READ modifie NOTIFY modifieChanged)
    Q_PROPERTY(QVariantMap entetes READ entetes NOTIFY entetesChanged)
    // Objet choisi (graphique, image) : son cadre, vide s'il n'y en a pas ; objetActif : graphique ouvert pour être
    // modifié dans le moteur ; objetTenu : on le déplace ou on l'agrandit à la souris (le moteur le suit)
    Q_PROPERTY(QRectF objet READ objet NOTIFY objetChanged)
    Q_PROPERTY(bool objetActif READ objetActif NOTIFY objetChanged)
    Q_PROPERTY(bool objetTenu READ objetTenu NOTIFY objetTenuChanged)
    // Augmente à chaque changement du contenu dessiné (pour relire ce qui en dépend : tableaux…)
    Q_PROPERTY(int revision READ revision NOTIFY revisionChanged)
    // Augmente quand une tuile dessinée arrive (pour relire couleurAu)
    Q_PROPERTY(int dessins READ dessins NOTIFY dessinsChanged)

public:
    enum Etat { Vide, Chargement, Pret, Erreur };
    Q_ENUM(Etat)

    explicit DocumentLO(QQuickItem *parent = nullptr);
    ~DocumentLO() override;

    QString chemin() const { return m_chemin; }
    int etat() const { return m_etat; }
    QString erreur() const { return m_erreur; }
    int typeDocument() const { return m_type; }
    qreal zoom() const { return m_zoom; }
    void setZoom(qreal z);
    qreal vueX() const { return m_vueX; }
    void setVueX(qreal x);
    qreal vueY() const { return m_vueY; }
    void setVueY(qreal y);
    qreal largeurDocument() const { return m_largeurTwips * m_zoom / 15.0; }
    qreal hauteurDocument() const { return m_hauteurTwips * m_zoom / 15.0; }
    int partie() const { return m_partie; }
    QStringList nomsParties() const { return m_nomsParties; }
    QRectF curseur() const { return enPixels(m_curseurTwips); }
    int colonne() const { return m_colonne; }
    int ligne() const { return m_ligne; }
    QString adresse() const { return m_adresse; }
    QString formule() const { return m_formule; }
    QVariantList selection() const;
    QRectF zoneSelection() const { return enPixels(m_zoneSelectionTwips); }
    QRectF curseurTexte() const { return enPixels(m_curseurTexteTwips); }
    bool curseurTexteVisible() const { return m_curseurTexteVisible; }
    QVariantMap etats() const { return m_etats; }
    bool modifie() const { return m_modifie; }
    QVariantMap entetes() const { return m_entetes; }
    QRectF objet() const { return enPixels(m_objetTwips); }
    bool objetActif() const { return m_objetActif; }
    bool objetTenu() const { return m_poignee >= 0; }
    int revision() const { return m_revision; }
    int dessins() const { return m_dessins; }
    // Couleur dessinée par le moteur en un point du document (pixels du document), ou transparent
    Q_INVOKABLE QColor couleurAu(qreal x, qreal y) const;

    // Ouvrir un fichier, ou un document vide (« calc », « writer »)
    Q_INVOKABLE void ouvrir(const QString &chemin);
    Q_INVOKABLE void nouveau(const QString &type);
    // Enregistrer à sa place (même format), ou sous un autre nom ; format : extension (« xlsx », « ods », « pdf »…)
    Q_INVOKABLE void enregistrer();
    Q_INVOKABLE void enregistrerSous(const QString &chemin, const QString &format = QString());
    // Commande de LibreOffice (« .uno:Bold »), arguments au format { Nom: { type, value } }
    Q_INVOKABLE void commande(const QString &nom, const QVariantMap &arguments = QVariantMap());
    Q_INVOKABLE void allerPartie(int partie);
    // Aller à une cellule (« F15 ») ; saisir le contenu de la cellule courante (barre de formule)
    Q_INVOKABLE void allerA(const QString &adresse);
    Q_INVOKABLE void saisir(const QString &contenu);
    // Valeurs d'une commande (getCommandValues) : réponse dans le signal valeurs
    Q_INVOKABLE void demanderValeurs(const QString &commande);
    // Touche envoyée par l'interface (raccourcis gérés en QML) : code de touche de LibreOffice, caractère
    Q_INVOKABLE void touche(int code, int caractere = 0);
    // Presse-papiers : contenu du moteur vers celui du système, et l'inverse avant de coller
    Q_INVOKABLE void copier(bool couper = false);
    Q_INVOKABLE void coller(bool valeursSeules = false);   // (valeurs seules : sans formules ni mise en forme)
    // Graphique de la sélection, du type demandé (rang dans la liste de l'assistant du moteur : 0 colonnes, 1 barres,
    // 2 secteurs, 4 aires, 5 lignes) ; l'assistant n'est pas montré, Sama le remplit
    Q_INVOKABLE void insererGraphique(int type);
    // Macro de Sama installée dans le moteur (« SamaTableaux.Creer ») avec des arguments texte ; la réponse arrive par
    // resultatScript(jeton, reussi, valeur). Rend le jeton.
    Q_INVOKABLE int script(const QString &fonction, const QVariantList &arguments = QVariantList());
    // Coordonnées : pixels de l'élément → pixels du document
    Q_INVOKABLE QPointF versDocument(qreal x, qreal y) const { return QPointF(x + m_vueX, y + m_vueY); }

signals:
    void cheminChanged();
    void etatChanged();
    void zoomChanged();
    void vueChanged();
    void tailleChanged();
    void partiesChanged();
    void curseurChanged();
    void adresseChanged();
    void formuleChanged();
    void selectionChanged();
    void curseurTexteChanged();
    void etatsChanged();
    void modifieChanged();
    void entetesChanged();
    void objetChanged();
    void objetTenuChanged();
    void revisionChanged();
    void dessinsChanged();
    void resultatScript(int jeton, bool reussi, const QString &valeur);
    void enregistre(bool reussi, const QString &chemin);
    void valeurs(const QString &commande, const QVariant &reponse);
    // Gestes de la personne (pixels de l'élément) : pour la mini-barre qui suit la sélection
    void pointeurAppuye();
    void pointeurRelache(qreal x, qreal y);
    void doubleClique();
    void clavierUtilise();
    void survole(qreal x, qreal y);

protected:
    QSGNode *updatePaintNode(QSGNode *ancien, UpdatePaintNodeData *) override;
    void geometryChange(const QRectF &nouvelle, const QRectF &ancienne) override;
    void keyPressEvent(QKeyEvent *e) override;
    void keyReleaseEvent(QKeyEvent *e) override;
    void inputMethodEvent(QInputMethodEvent *e) override;
    QVariant inputMethodQuery(Qt::InputMethodQuery requete) const override;
    void mousePressEvent(QMouseEvent *e) override;
    void mouseMoveEvent(QMouseEvent *e) override;
    void mouseReleaseEvent(QMouseEvent *e) override;
    void mouseDoubleClickEvent(QMouseEvent *e) override;
    void hoverMoveEvent(QHoverEvent *e) override;
    void wheelEvent(QWheelEvent *e) override;
    void releaseResources() override;

private:
    struct Tuile {
        QImage image;
        bool perimee = false;     // à redessiner (l'ancienne image reste affichée en attendant)
        bool demandee = false;    // dessin en cours dans le moteur
        bool neuve = false;       // image pas encore passée à la carte graphique
        quint64 version = 0;
    };
    using Cle = quint64;          // partie, zoom, colonne et ligne de tuiles
    Cle cle(int i, int j) const;

    void annonce(int type, const QByteArray &charge);
    void charger(const QString &url);
    void apresChargement(_LibreOfficeKitDocument *doc, const QString &erreur, int type, long l, long h,
                         int partie, const QStringList &noms);
    void planifier();
    void invalider(const QRectF &twips, int partie);
    void relirePartiesEtTaille();
    void signalerVue();
    void relireEntetes();
    void souris(int type, QMouseEvent *e, int nombre);
    void envoyerSouris(int type, QPointF position, int nombre, int boutons, int mod);
    void survoler();
    void dialogue(const QByteArray &charge);
    void pointeur(const QByteArray &nom);
    QRectF enPixels(const QRectF &twips) const;
    qreal twipsParTuile() const;
    int pixelsParTuile() const;

    _LibreOfficeKitDocument *m_doc = nullptr;   // utilisé seulement dans le fil du moteur
    Relais *m_relais = nullptr;
    QString m_chemin, m_erreur, m_adresse, m_formule;
    int m_etat = Vide, m_type = -1, m_partie = 0, m_colonne = -1, m_ligne = -1;
    qreal m_zoom = 1.0, m_vueX = 0, m_vueY = 0;
    long m_largeurTwips = 0, m_hauteurTwips = 0;
    QStringList m_nomsParties;
    QRectF m_curseurTwips, m_zoneSelectionTwips, m_curseurTexteTwips;
    QList<QRectF> m_selectionTwips;
    bool m_curseurTexteVisible = false, m_modifie = false;
    QVariantMap m_etats, m_entetes;
    QHash<Cle, Tuile> m_tuiles;
    QHash<Cle, QSGTexture *> m_textures;        // (fil de rendu)
    QSet<Cle> m_aRetirer;
    bool m_vueEnvoyee = false, m_entetesDemandes = false, m_entetesARelire = false;
    QRectF m_derniereVue;
    // Objet choisi, et la poignée tenue à la souris (0 à 7 depuis le coin haut gauche, dans le sens des aiguilles d'une
    // montre ; 8 : l'objet entier), avec le décalage vers la poignée exacte (pixels du document)
    QRectF m_objetTwips;
    bool m_objetActif = false;
    int m_poignee = -1;
    QPointF m_decalage;
    int m_graphiqueEnAttente = -1;
    int m_revision = 0, m_dernierJeton = 0, m_dessins = 0;
    QHash<QByteArray, QQueue<int>> m_scriptsEnAttente;   // par adresse de macro, dans l'ordre des appels
    bool m_survolEnvoye = false;
    QPointF m_survolSuivant;
    bool m_survolAttend = false;
};
