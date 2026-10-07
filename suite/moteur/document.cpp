#include "document.h"
#include "moteur.h"

#include <QClipboard>
#include <QGuiApplication>
#include <QJsonDocument>
#include <QJsonObject>
#include <QLineF>
#include <QMimeData>
#include <QQuickWindow>
#include <QRegularExpression>
#include <QSGSimpleTextureNode>
#include <QSGTexture>
#include <QThread>
#include <QTimer>
#include <QUrl>
#include <QtMath>

namespace {

constexpr qreal TWIPS_PAR_PIXEL = 15.0;          // 1 pouce = 1440 twips = 96 pixels, au zoom 100 %
constexpr int TUILE = 256;                        // pixels de l'écran par tuile
constexpr const char *MIME_SAMA = "application/x-sama-moteur";   // copié depuis le moteur (collage fidèle)

// Touches de LibreOffice (com::sun::star::awt::Key) et modificateurs (vcl)
enum {
    K_NUM0 = 256, K_A = 512, K_F1 = 768, K_DOWN = 1024, K_UP, K_LEFT, K_RIGHT, K_HOME, K_END, K_PAGEUP, K_PAGEDOWN,
    K_RETURN = 1280, K_ESCAPE, K_TAB, K_BACKSPACE, K_SPACE, K_INSERT, K_DELETE,
    M_SHIFT = 0x1000, M_CTRL = 0x2000, M_ALT = 0x4000
};

int modificateurs(Qt::KeyboardModifiers m)
{
    int r = 0;
    if (m & Qt::ShiftModifier) r |= M_SHIFT;
    if (m & Qt::ControlModifier) r |= M_CTRL;
    if (m & Qt::AltModifier) r |= M_ALT;
    return r;
}

// Touche de Qt → touche de LibreOffice (0 : pas une touche de commande)
int codeTouche(int k)
{
    switch (k) {
    case Qt::Key_Down: return K_DOWN;
    case Qt::Key_Up: return K_UP;
    case Qt::Key_Left: return K_LEFT;
    case Qt::Key_Right: return K_RIGHT;
    case Qt::Key_Home: return K_HOME;
    case Qt::Key_End: return K_END;
    case Qt::Key_PageUp: return K_PAGEUP;
    case Qt::Key_PageDown: return K_PAGEDOWN;
    case Qt::Key_Return: case Qt::Key_Enter: return K_RETURN;
    case Qt::Key_Escape: return K_ESCAPE;
    case Qt::Key_Tab: case Qt::Key_Backtab: return K_TAB;
    case Qt::Key_Backspace: return K_BACKSPACE;
    case Qt::Key_Insert: return K_INSERT;
    case Qt::Key_Delete: return K_DELETE;
    default: break;
    }
    if (k >= Qt::Key_F1 && k <= Qt::Key_F12) return K_F1 + (k - Qt::Key_F1);
    if (k >= Qt::Key_A && k <= Qt::Key_Z) return K_A + (k - Qt::Key_A);
    if (k >= Qt::Key_0 && k <= Qt::Key_9) return K_NUM0 + (k - Qt::Key_0);
    if (k == Qt::Key_Space) return K_SPACE;
    return 0;
}

// « x, y, l, h[, …] » → rectangle (twips) ; « EMPTY » ou vide → rectangle nul
QRectF rectangle(const QByteArray &s)
{
    const QList<QByteArray> p = s.split(',');
    if (p.size() < 4 || s.startsWith("EMPTY")) return QRectF();
    return QRectF(p[0].trimmed().toDouble(), p[1].trimmed().toDouble(), p[2].trimmed().toDouble(), p[3].trimmed().toDouble());
}

// Poignées d'un objet : coins et milieux des côtés, depuis le coin haut gauche, dans le sens des aiguilles d'une montre
QList<QPointF> poignees(const QRectF &r)
{
    return {r.topLeft(), QPointF(r.center().x(), r.top()), r.topRight(), QPointF(r.right(), r.center().y()),
            r.bottomRight(), QPointF(r.center().x(), r.bottom()), r.bottomLeft(), QPointF(r.left(), r.center().y())};
}

QByteArray versJson(const QVariantMap &m)
{
    return m.isEmpty() ? QByteArray() : QJsonDocument::fromVariant(m).toJson(QJsonDocument::Compact);
}

void rappelLok(int type, const char *charge, void *donnees)
{
    // (fil de LibreOffice : le relais passe l'annonce au fil de l'interface)
    emit static_cast<Relais *>(donnees)->annonce(type, QByteArray(charge ? charge : ""));
}

} // namespace

DocumentLO::DocumentLO(QQuickItem *parent)
    : QQuickItem(parent)
{
    setFlag(ItemHasContents, true);
    setFlag(ItemAcceptsInputMethod, true);
    setFlag(ItemIsFocusScope, false);
    setClip(true);
    setAcceptedMouseButtons(Qt::AllButtons);
    setAcceptHoverEvents(true);
    setActiveFocusOnTab(true);
    m_relais = new Relais();
    connect(m_relais, &Relais::annonce, this, &DocumentLO::annonce, Qt::QueuedConnection);
}

DocumentLO::~DocumentLO()
{
    auto *doc = m_doc;
    Moteur::instance()->executerEtAttendre([doc] {
        if (doc) {
            Moteur::instance()->retirer(doc);
            doc->pClass->registerCallback(doc, nullptr, nullptr);
            doc->pClass->destroy(doc);
        }
    });
    delete m_relais;
    for (QSGTexture *t : std::as_const(m_textures)) t->deleteLater();   // (elles appartiennent au fil de rendu)
}

// ——— Ouverture ———

void DocumentLO::ouvrir(const QString &chemin)
{
    m_chemin = chemin;
    emit cheminChanged();
    charger(QUrl::fromLocalFile(chemin).toString(QUrl::FullyEncoded));
}

void DocumentLO::nouveau(const QString &type)
{
    m_chemin.clear();
    emit cheminChanged();
    charger(type == QLatin1String("writer") ? QStringLiteral("private:factory/swriter") : QStringLiteral("private:factory/scalc"));
}

void DocumentLO::charger(const QString &url)
{
    m_etat = Chargement;
    m_erreur.clear();
    m_tuiles.clear();
    m_aRetirer.unite(QSet<Cle>(m_textures.keyBegin(), m_textures.keyEnd()));
    m_vueX = m_vueY = 0;
    m_etats.clear();
    m_scriptsEnAttente.clear();
    m_entetes.clear();
    m_objetTwips = QRectF();
    m_objetActif = false;
    m_poignee = m_graphiqueEnAttente = -1;
    emit objetChanged();
    emit objetTenuChanged();
    emit etatChanged();
    emit vueChanged();
    auto *ancien = m_doc;
    m_doc = nullptr;
    Relais *relais = m_relais;
    Moteur::instance()->executer([this, url, ancien, relais] {
        Moteur *moteur = Moteur::instance();
        if (ancien) {
            moteur->retirer(ancien);
            ancien->pClass->registerCallback(ancien, nullptr, nullptr);
            ancien->pClass->destroy(ancien);
        }
        LibreOfficeKit *lok = moteur->lok();
        LibreOfficeKitDocument *doc = nullptr;
        QString erreur;
        int type = -1, partie = 0;
        long l = 0, h = 0;
        QStringList noms;
        if (!lok) {
            erreur = moteur->erreur();
        } else {
            // (interface et noms des fonctions en français : =SOMME, =SI…)
            doc = lok->pClass->documentLoadWithOptions(lok, url.toUtf8().constData(), "Language=fr-FR");
            if (!doc) {
                char *e = lok->pClass->getError(lok);
                erreur = e && *e ? QString::fromUtf8(e) : QStringLiteral("Ce fichier n'a pas pu être ouvert.");
                if (e) lok->pClass->freeError(e);
            } else {
                moteur->ajouter(doc);
                doc->pClass->initializeForRendering(doc, "{}");
                doc->pClass->registerCallback(doc, &rappelLok, relais);
                type = doc->pClass->getDocumentType(doc);
                doc->pClass->getDocumentSize(doc, &l, &h);
                partie = doc->pClass->getPart(doc);
                for (int i = 0, n = doc->pClass->getParts(doc); i < n; ++i) {
                    char *nom = doc->pClass->getPartName(doc, i);
                    noms << QString::fromUtf8(nom ? nom : "");
                    free(nom);
                }
            }
        }
        QMetaObject::invokeMethod(this, [this, doc, erreur, type, l, h, partie, noms] {
            apresChargement(doc, erreur, type, l, h, partie, noms);
        }, Qt::QueuedConnection);
    });
}

void DocumentLO::apresChargement(_LibreOfficeKitDocument *doc, const QString &erreur, int type, long l, long h,
                                 int partie, const QStringList &noms)
{
    m_doc = doc;
    m_erreur = erreur;
    m_etat = doc ? Pret : Erreur;
    m_type = type;
    m_largeurTwips = l;
    m_hauteurTwips = h;
    m_partie = partie;
    m_nomsParties = noms;
    m_modifie = false;
    m_vueEnvoyee = false;
    emit etatChanged();
    emit tailleChanged();
    emit partiesChanged();
    emit modifieChanged();
    if (!doc) return;
    signalerVue();
    planifier();
    relireEntetes();
}

// ——— Vue, zoom et taille ———

void DocumentLO::setZoom(qreal z)
{
    z = qBound(0.25, z, 4.0);
    if (qFuzzyCompare(z, m_zoom)) return;
    // (le point au centre de la vue reste au centre)
    const qreal cx = (m_vueX + width() / 2) / m_zoom, cy = (m_vueY + height() / 2) / m_zoom;
    m_zoom = z;
    m_vueX = qMax<qreal>(0, cx * z - width() / 2);
    m_vueY = qMax<qreal>(0, cy * z - height() / 2);
    emit zoomChanged();
    emit tailleChanged();
    emit vueChanged();
    emit curseurChanged();
    emit selectionChanged();
    emit curseurTexteChanged();
    emit objetChanged();
    m_vueEnvoyee = false;
    signalerVue();
    planifier();
    relireEntetes();
}

void DocumentLO::setVueX(qreal x)
{
    x = qMax<qreal>(0, x);
    if (qFuzzyCompare(x + 1, m_vueX + 1)) return;
    m_vueX = x;
    emit vueChanged();
    signalerVue();
    planifier();
    relireEntetes();
}

void DocumentLO::setVueY(qreal y)
{
    y = qMax<qreal>(0, y);
    if (qFuzzyCompare(y + 1, m_vueY + 1)) return;
    m_vueY = y;
    emit vueChanged();
    signalerVue();
    planifier();
    relireEntetes();
}

void DocumentLO::geometryChange(const QRectF &nouvelle, const QRectF &ancienne)
{
    QQuickItem::geometryChange(nouvelle, ancienne);
    if (nouvelle.size() != ancienne.size()) {
        signalerVue();
        planifier();
        relireEntetes();
    }
}

// Le moteur doit connaître la partie visible (le tableur agrandit le document quand on y va) et le zoom
void DocumentLO::signalerVue()
{
    if (!m_doc || width() <= 0 || height() <= 0) return;
    const QRectF vue(m_vueX * TWIPS_PAR_PIXEL / m_zoom, m_vueY * TWIPS_PAR_PIXEL / m_zoom,
                     width() * TWIPS_PAR_PIXEL / m_zoom, height() * TWIPS_PAR_PIXEL / m_zoom);
    const bool zoomAEnvoyer = !m_vueEnvoyee;
    if (!zoomAEnvoyer && vue == m_derniereVue) return;
    m_derniereVue = vue;
    m_vueEnvoyee = true;
    auto *doc = m_doc;
    const int tp = pixelsParTuile(), tt = qRound(twipsParTuile());
    Moteur::instance()->executer([doc, vue, zoomAEnvoyer, tp, tt] {
        if (zoomAEnvoyer) doc->pClass->setClientZoom(doc, tp, tp, tt, tt);
        doc->pClass->setClientVisibleArea(doc, int(vue.x()), int(vue.y()), int(vue.width()), int(vue.height()));
    });
}

int DocumentLO::pixelsParTuile() const { return TUILE; }

qreal DocumentLO::twipsParTuile() const
{
    const qreal dpr = window() ? window()->effectiveDevicePixelRatio() : 1.0;
    return qRound(TUILE / dpr * TWIPS_PAR_PIXEL / m_zoom);
}

QRectF DocumentLO::enPixels(const QRectF &t) const
{
    if (t.isNull()) return QRectF();
    const qreal f = m_zoom / TWIPS_PAR_PIXEL;
    return QRectF(t.x() * f, t.y() * f, t.width() * f, t.height() * f);
}

QVariantList DocumentLO::selection() const
{
    QVariantList l;
    for (const QRectF &r : m_selectionTwips) l << enPixels(r);
    return l;
}

// ——— Tuiles ———

DocumentLO::Cle DocumentLO::cle(int i, int j) const
{
    const quint64 z = quint64(qRound(m_zoom * 100)) & 0xfff;
    return (quint64(m_partie & 0xff) << 56) | (z << 44) | (quint64(i & 0x3fffff) << 22) | quint64(j & 0x3fffff);
}

void DocumentLO::planifier()
{
    if (m_etat != Pret || !m_doc || width() <= 0 || height() <= 0) return;
    const qreal tt = twipsParTuile();
    const int tp = pixelsParTuile();
    const qreal f = TWIPS_PAR_PIXEL / m_zoom;
    // Tuiles visibles, plus une rangée autour (le défilement trouve ses tuiles prêtes)
    const int i0 = qMax(0, int(std::floor(m_vueX * f / tt)) - 1), i1 = int(std::floor((m_vueX + width()) * f / tt)) + 1;
    const int j0 = qMax(0, int(std::floor(m_vueY * f / tt)) - 1), j1 = int(std::floor((m_vueY + height()) * f / tt)) + 1;
    QSet<Cle> utiles;
    for (int j = j0; j <= j1; ++j) {
        for (int i = i0; i <= i1; ++i) {
            const Cle k = cle(i, j);
            utiles.insert(k);
            Tuile &t = m_tuiles[k];
            if ((!t.image.isNull() && !t.perimee) || t.demandee) continue;
            t.demandee = true;
            t.perimee = false;
            const quint64 version = ++t.version;
            auto *doc = m_doc;
            const int partie = m_partie, x = qRound(i * tt), y = qRound(j * tt), cote = qRound(tt);
            Moteur::instance()->executer([this, doc, k, version, partie, x, y, cote, tp] {
                QImage image(tp, tp, QImage::Format_ARGB32_Premultiplied);
                image.fill(Qt::white);
                doc->pClass->paintPartTile(doc, image.bits(), partie, 0, tp, tp, x, y, cote, cote);
                QMetaObject::invokeMethod(this, [this, k, version, image] {
                    auto it = m_tuiles.find(k);
                    if (it == m_tuiles.end()) return;
                    it->demandee = false;
                    if (it->version == version) {
                        it->image = image;
                        it->neuve = true;
                        ++m_dessins;
                        emit dessinsChanged();
                    }
                    if (it->perimee) planifier();
                    update();
                }, Qt::QueuedConnection);
            });
        }
    }
    // Les tuiles loin de la vue (autre feuille, autre zoom, défilées) sont oubliées
    for (auto it = m_tuiles.begin(); it != m_tuiles.end();) {
        if (!utiles.contains(it.key()) && !it->demandee) {
            m_aRetirer.insert(it.key());
            it = m_tuiles.erase(it);
        } else {
            ++it;
        }
    }
    update();
}

QColor DocumentLO::couleurAu(qreal x, qreal y) const
{
    const qreal f = TWIPS_PAR_PIXEL / m_zoom, tt = twipsParTuile();
    const int i = int(std::floor(x * f / tt)), j = int(std::floor(y * f / tt));
    if (i < 0 || j < 0) return Qt::transparent;
    auto it = m_tuiles.constFind(cle(i, j));
    if (it == m_tuiles.constEnd() || it->image.isNull()) return Qt::transparent;
    const int px = qBound(0, int((x * f - i * tt) / tt * it->image.width()), it->image.width() - 1);
    const int py = qBound(0, int((y * f - j * tt) / tt * it->image.height()), it->image.height() - 1);
    return QColor::fromRgba(it->image.pixel(px, py));
}

void DocumentLO::invalider(const QRectF &twips, int partie)
{
    for (auto it = m_tuiles.begin(); it != m_tuiles.end(); ++it) {
        const int p = int(it.key() >> 56);
        if (partie >= 0 && p != partie) continue;
        if (!twips.isNull()) {
            const int i = int((it.key() >> 22) & 0x3fffff), j = int(it.key() & 0x3fffff);
            const quint64 z = (it.key() >> 44) & 0xfff;
            const qreal cote = qRound(TUILE / (window() ? window()->effectiveDevicePixelRatio() : 1.0) * TWIPS_PAR_PIXEL / (z / 100.0));
            if (!QRectF(i * cote, j * cote, cote, cote).intersects(twips)) continue;
        }
        it->perimee = true;
        ++it->version;          // (un dessin en cours est déjà dépassé)
    }
    planifier();
}

QSGNode *DocumentLO::updatePaintNode(QSGNode *ancien, UpdatePaintNodeData *)
{
    QSGNode *racine = ancien ? ancien : new QSGNode();
    while (QSGNode *n = racine->firstChild()) {
        racine->removeChildNode(n);
        delete n;
    }
    for (const Cle k : std::as_const(m_aRetirer)) {
        delete m_textures.take(k);
    }
    m_aRetirer.clear();
    if (!window()) return racine;
    const qreal f = m_zoom / TWIPS_PAR_PIXEL, tt = twipsParTuile();
    for (auto it = m_tuiles.begin(); it != m_tuiles.end(); ++it) {
        if (it->image.isNull()) continue;
        if (int(it.key() >> 56) != m_partie || (((it.key() >> 44) & 0xfff) != quint64(qRound(m_zoom * 100)))) continue;
        QSGTexture *&texture = m_textures[it.key()];
        if (!texture || it->neuve) {
            delete texture;
            texture = window()->createTextureFromImage(it->image);
            it->neuve = false;
        }
        const int i = int((it.key() >> 22) & 0x3fffff), j = int(it.key() & 0x3fffff);
        const qreal x = std::round(i * tt * f - m_vueX), y = std::round(j * tt * f - m_vueY);
        const qreal x2 = std::round((i + 1) * tt * f - m_vueX), y2 = std::round((j + 1) * tt * f - m_vueY);
        if (x2 < 0 || y2 < 0 || x > width() || y > height()) continue;
        auto *noeud = new QSGSimpleTextureNode();
        noeud->setTexture(texture);
        noeud->setOwnsTexture(false);
        noeud->setRect(QRectF(x, y, x2 - x, y2 - y));
        noeud->setFiltering(QSGTexture::Linear);
        racine->appendChildNode(noeud);
    }
    return racine;
}

void DocumentLO::releaseResources()
{
    // (la fenêtre s'en va : les textures appartiennent à son rendu)
    if (window()) {
        const QList<QSGTexture *> textures = m_textures.values();
        for (QSGTexture *t : textures) t->deleteLater();
    }
    m_textures.clear();
    for (auto &t : m_tuiles) t.neuve = true;
}

// ——— Annonces du moteur ———

void DocumentLO::annonce(int type, const QByteArray &charge)
{
    switch (type) {
    case LOK_CALLBACK_UNO_COMMAND_RESULT: {
        // Réponse d'une macro de Sama : on la rend à qui l'a demandée
        const QJsonObject o = QJsonDocument::fromJson(charge).object();
        const QByteArray nom = o.value(QStringLiteral("commandName")).toString().toUtf8();
        auto it = m_scriptsEnAttente.find(nom);
        if (it == m_scriptsEnAttente.end() || it->isEmpty()) break;
        const int jeton = it->dequeue();
        if (it->isEmpty()) m_scriptsEnAttente.erase(it);
        emit resultatScript(jeton, o.value(QStringLiteral("success")).toBool(),
                            o.value(QStringLiteral("result")).toObject().value(QStringLiteral("value")).toString());
        break;
    }
    case LOK_CALLBACK_INVALIDATE_TILES: {
        ++m_revision;
        emit revisionChanged();
        const QList<QByteArray> p = charge.split(',');
        int partie = -1;
        if (charge.startsWith("EMPTY")) {
            if (p.size() > 1) partie = p[1].trimmed().toInt();
            invalider(QRectF(), partie);
        } else {
            if (p.size() > 4) partie = p[4].trimmed().toInt();
            invalider(rectangle(charge), partie);
        }
        break;
    }
    case LOK_CALLBACK_CELL_CURSOR: {
        const QList<QByteArray> p = charge.split(',');
        m_curseurTwips = rectangle(charge);
        m_colonne = p.size() > 5 ? p[4].trimmed().toInt() : -1;
        m_ligne = p.size() > 5 ? p[5].trimmed().toInt() : -1;
        emit curseurChanged();
        break;
    }
    case LOK_CALLBACK_CELL_FORMULA:
        m_formule = QString::fromUtf8(charge);
        emit formuleChanged();
        break;
    case LOK_CALLBACK_CELL_ADDRESS:
        m_adresse = QString::fromUtf8(charge);
        emit adresseChanged();
        break;
    case LOK_CALLBACK_TEXT_SELECTION: {
        m_selectionTwips.clear();
        for (const QByteArray &morceau : charge.split(';')) {
            const QRectF r = rectangle(morceau);
            if (!r.isNull()) m_selectionTwips << r;
        }
        emit selectionChanged();
        break;
    }
    case LOK_CALLBACK_CELL_SELECTION_AREA:
        m_zoneSelectionTwips = rectangle(charge);
        emit selectionChanged();
        break;
    case LOK_CALLBACK_INVALIDATE_VISIBLE_CURSOR: {
        if (charge.startsWith('{')) {
            const QJsonObject o = QJsonDocument::fromJson(charge).object();
            m_curseurTexteTwips = rectangle(o.value(QStringLiteral("rectangle")).toString().toUtf8());
        } else {
            m_curseurTexteTwips = rectangle(charge);
        }
        emit curseurTexteChanged();
        break;
    }
    case LOK_CALLBACK_CURSOR_VISIBLE:
        m_curseurTexteVisible = charge == "true";
        emit curseurTexteChanged();
        break;
    case LOK_CALLBACK_STATE_CHANGED: {
        QString nom, valeur;
        if (charge.startsWith('{')) {
            const QJsonObject o = QJsonDocument::fromJson(charge).object();
            nom = o.value(QStringLiteral("commandName")).toString();
            const QJsonValue v = o.value(QStringLiteral("state"));
            valeur = v.isString() ? v.toString() : QString::fromUtf8(QJsonDocument(v.toObject()).toJson(QJsonDocument::Compact));
        } else {
            const int egal = charge.indexOf('=');
            if (egal < 0) break;
            nom = QString::fromUtf8(charge.left(egal));
            valeur = QString::fromUtf8(charge.mid(egal + 1));
        }
        if (nom == QLatin1String(".uno:ModifiedStatus")) {
            const bool m = valeur == QLatin1String("true");
            if (m != m_modifie) {
                m_modifie = m;
                emit modifieChanged();
            }
        }
        m_etats.insert(nom, valeur);
        emit etatsChanged();
        break;
    }
    case LOK_CALLBACK_GRAPHIC_SELECTION:
        // « x, y, l, h, angle, {…} » ; « EMPTY » : plus d'objet choisi ; « INPLACE » : graphique ouvert dans le moteur
        if (charge.startsWith("INPLACE EXIT")) {
            m_objetActif = false;
        } else if (charge.startsWith("INPLACE")) {
            m_objetActif = true;
        } else {
            m_objetTwips = rectangle(charge);
            if (m_objetTwips.isNull()) m_objetActif = false;
        }
        emit objetChanged();
        break;
    case LOK_CALLBACK_MOUSE_POINTER:
        pointeur(charge.trimmed());
        break;
    case LOK_CALLBACK_JSDIALOG:
        dialogue(charge);
        break;
    case LOK_CALLBACK_DOCUMENT_SIZE_CHANGED:
    case LOK_CALLBACK_SET_PART:
        relirePartiesEtTaille();
        break;
    case LOK_CALLBACK_INVALIDATE_HEADER:
    case LOK_CALLBACK_INVALIDATE_SHEET_GEOMETRY:
        relireEntetes();
        break;
    default:
        break;
    }
}

void DocumentLO::relirePartiesEtTaille()
{
    if (!m_doc) return;
    auto *doc = m_doc;
    Moteur::instance()->executer([this, doc] {
        long l = 0, h = 0;
        doc->pClass->getDocumentSize(doc, &l, &h);
        const int partie = doc->pClass->getPart(doc);
        QStringList noms;
        for (int i = 0, n = doc->pClass->getParts(doc); i < n; ++i) {
            char *nom = doc->pClass->getPartName(doc, i);
            noms << QString::fromUtf8(nom ? nom : "");
            free(nom);
        }
        QMetaObject::invokeMethod(this, [this, l, h, partie, noms] {
            const bool autrePartie = partie != m_partie;
            m_largeurTwips = l;
            m_hauteurTwips = h;
            m_partie = partie;
            m_nomsParties = noms;
            emit tailleChanged();
            emit partiesChanged();
            if (autrePartie) {
                planifier();
                relireEntetes();
            }
        }, Qt::QueuedConnection);
    });
}

// En-têtes de lignes et de colonnes de la partie visible (tableur) : { rows: [{ text, size }], columns: […] },
// « size » = fin de la ligne ou de la colonne, en pixels du document au zoom 100 %
void DocumentLO::relireEntetes()
{
    if (!m_doc || m_type != LOK_DOCTYPE_SPREADSHEET || width() <= 0) return;
    if (m_entetesDemandes) {
        m_entetesARelire = true;
        return;
    }
    m_entetesDemandes = true;
    auto *doc = m_doc;
    const qreal f = TWIPS_PAR_PIXEL / m_zoom;
    const QByteArray demande = QStringLiteral(".uno:ViewRowColumnHeaders?x=%1&y=%2&width=%3&height=%4")
                                   .arg(qRound(m_vueX * f)).arg(qRound(m_vueY * f))
                                   .arg(qRound(width() * f)).arg(qRound(height() * f)).toUtf8();
    Moteur::instance()->executer([this, doc, demande] {
        char *r = doc->pClass->getCommandValues(doc, demande.constData());
        const QVariantMap entetes = QJsonDocument::fromJson(QByteArray(r ? r : "{}")).object().toVariantMap();
        free(r);
        QMetaObject::invokeMethod(this, [this, entetes] {
            m_entetesDemandes = false;
            m_entetes = entetes;
            emit entetesChanged();
            if (m_entetesARelire) {
                m_entetesARelire = false;
                relireEntetes();
            }
        }, Qt::QueuedConnection);
    });
}

// ——— Commandes ———

void DocumentLO::commande(const QString &nom, const QVariantMap &arguments)
{
    if (!m_doc) return;
    auto *doc = m_doc;
    const QByteArray n = nom.toUtf8(), a = versJson(arguments);
    Moteur::instance()->executer([doc, n, a] {
        doc->pClass->postUnoCommand(doc, n.constData(), a.isEmpty() ? nullptr : a.constData(), true);
    });
}

int DocumentLO::script(const QString &fonction, const QVariantList &arguments)
{
    if (!m_doc) return -1;
    const int jeton = ++m_dernierJeton;
    const QByteArray url = QStringLiteral("vnd.sun.star.script:Standard.%1?language=Basic&location=application").arg(fonction).toUtf8();
    // (les arguments passent dans l'ordre de leurs noms : p00, p01…)
    QJsonObject args;
    for (int i = 0; i < arguments.size(); ++i)
        args.insert(QStringLiteral("p%1").arg(i, 2, 10, QLatin1Char('0')),
                    QJsonObject{{QStringLiteral("type"), QStringLiteral("string")}, {QStringLiteral("value"), arguments.at(i).toString()}});
    const QByteArray a = arguments.isEmpty() ? QByteArray() : QJsonDocument(args).toJson(QJsonDocument::Compact);
    m_scriptsEnAttente[url].enqueue(jeton);
    auto *doc = m_doc;
    Moteur::instance()->executer([doc, url, a] {
        doc->pClass->postUnoCommand(doc, url.constData(), a.isEmpty() ? nullptr : a.constData(), true);
    });
    return jeton;
}

void DocumentLO::insererGraphique(int type)
{
    if (!m_doc) return;
    m_graphiqueEnAttente = qMax(0, type);
    commande(QStringLiteral(".uno:InsertObjectChart"));
}

// Fenêtres du moteur (décrites en JSON, jamais montrées telles quelles). L'assistant de graphique : Sama choisit le
// type demandé et le termine aussitôt
void DocumentLO::dialogue(const QByteArray &charge)
{
    // (une fenêtre du moteur que Sama ne montre pas : on la note, pour la retrouver)
    if (charge.contains("\"dialogid\"") && !charge.contains("CHART2_HID_SCH_WIZARD_ROADMAP"))
        qWarning("Fenêtre du moteur non affichée : %s", charge.left(400).constData());
    if (m_graphiqueEnAttente < 0 || !m_doc || !charge.contains("CHART2_HID_SCH_WIZARD_ROADMAP")) return;
    // (numéro de la fenêtre : le dernier « "id": nombre » de l'annonce)
    unsigned long long fenetre = 0;
    static const QRegularExpression numero(QStringLiteral("\"id\"\\s*:\\s*(\\d+)"));
    for (auto it = numero.globalMatch(QString::fromUtf8(charge)); it.hasNext();) fenetre = it.next().captured(1).toULongLong();
    if (!fenetre) return;
    const QByteArray choix = QStringLiteral(R"x({"id":"charttype","cmd":"select","data":"%1","type":"treeview"})x")
                                 .arg(m_graphiqueEnAttente).toUtf8();
    // (lignes : la variante « lignes seules », la 3e des 4 — sinon le moteur ne met que des points)
    const bool lignes = m_graphiqueEnAttente == 5;
    m_graphiqueEnAttente = -1;
    auto *doc = m_doc;
    Moteur::instance()->executer([doc, fenetre, choix, lignes] {
        doc->pClass->sendDialogEvent(doc, fenetre, choix.constData());
        if (lignes)
            doc->pClass->sendDialogEvent(doc, fenetre, R"x({"id":"subtype","cmd":"click","data":"0.625;0.5","type":"drawingarea"})x");
        doc->pClass->sendDialogEvent(doc, fenetre, R"x({"id":"finish","cmd":"click","data":"","type":"pushbutton"})x");
    });
}

// Forme du pointeur demandée par le moteur (au-dessus d'un objet, d'une poignée, d'un texte…)
void DocumentLO::pointeur(const QByteArray &nom)
{
    Qt::CursorShape forme = Qt::ArrowCursor;
    if (nom == "text") forme = Qt::IBeamCursor;
    else if (nom == "pointer") forme = Qt::PointingHandCursor;
    else if (nom == "move") forme = Qt::SizeAllCursor;
    else if (nom == "col-resize") forme = Qt::SplitHCursor;
    else if (nom == "row-resize") forme = Qt::SplitVCursor;
    else if (nom == "n-resize" || nom == "s-resize" || nom == "ns-resize") forme = Qt::SizeVerCursor;
    else if (nom == "e-resize" || nom == "w-resize" || nom == "ew-resize") forme = Qt::SizeHorCursor;
    else if (nom == "nw-resize" || nom == "se-resize" || nom == "nwse-resize") forme = Qt::SizeFDiagCursor;
    else if (nom == "ne-resize" || nom == "sw-resize" || nom == "nesw-resize") forme = Qt::SizeBDiagCursor;
    else if (nom == "crosshair" || nom == "cell") forme = Qt::CrossCursor;
    setCursor(forme);
}

void DocumentLO::allerPartie(int partie)
{
    if (!m_doc || partie < 0 || partie >= m_nomsParties.size() || partie == m_partie) return;
    auto *doc = m_doc;
    Moteur::instance()->executer([doc, partie] { doc->pClass->setPart(doc, partie); });
    m_partie = partie;
    m_vueX = m_vueY = 0;
    emit partiesChanged();
    emit vueChanged();
    m_vueEnvoyee = false;
    signalerVue();
    relirePartiesEtTaille();
    planifier();
    relireEntetes();
}

void DocumentLO::allerA(const QString &adresse)
{
    commande(QStringLiteral(".uno:GoToCell"),
             {{QStringLiteral("ToPoint"), QVariantMap{{QStringLiteral("type"), QStringLiteral("string")},
                                                      {QStringLiteral("value"), adresse}}}});
}

void DocumentLO::saisir(const QString &contenu)
{
    commande(QStringLiteral(".uno:EnterString"),
             {{QStringLiteral("StringName"), QVariantMap{{QStringLiteral("type"), QStringLiteral("string")},
                                                         {QStringLiteral("value"), contenu}}}});
}

void DocumentLO::demanderValeurs(const QString &commande)
{
    if (!m_doc) return;
    auto *doc = m_doc;
    const QByteArray c = commande.toUtf8();
    Moteur::instance()->executer([this, doc, c] {
        char *r = doc->pClass->getCommandValues(doc, c.constData());
        const QByteArray texte(r ? r : "");
        free(r);
        QMetaObject::invokeMethod(this, [this, c, texte] {
            const QJsonDocument j = QJsonDocument::fromJson(texte);
            emit valeurs(QString::fromUtf8(c), j.isNull() ? QVariant(QString::fromUtf8(texte)) : j.toVariant());
        }, Qt::QueuedConnection);
    });
}

void DocumentLO::enregistrer()
{
    if (m_chemin.isEmpty()) {
        emit enregistre(false, QString());     // (l'interface demande où l'enregistrer)
        return;
    }
    enregistrerSous(m_chemin);
}

void DocumentLO::enregistrerSous(const QString &chemin, const QString &format)
{
    if (!m_doc) return;
    auto *doc = m_doc;
    const QString f = format.isEmpty() ? chemin.section(QLatin1Char('.'), -1).toLower() : format;
    const QByteArray url = QUrl::fromLocalFile(chemin).toString(QUrl::FullyEncoded).toUtf8(), fb = f.toUtf8();
    // (saveAs écrit une copie : le moteur se croit encore « modifié ». Sauf pour un export PDF, on lui dit qu'il ne
    // l'est plus ; il le signalera de lui-même à la prochaine modification)
    const bool exportation = f == QLatin1String("pdf");
    Moteur::instance()->executer([this, doc, url, fb, chemin, exportation] {
        const bool ok = doc->pClass->saveAs(doc, url.constData(), fb.isEmpty() ? nullptr : fb.constData(), nullptr);
        if (ok && !exportation)
            doc->pClass->postUnoCommand(doc, ".uno:Modified", "{\"Modified\":{\"type\":\"boolean\",\"value\":false}}", false);
        QMetaObject::invokeMethod(this, [this, ok, chemin, exportation] {
            if (ok && !exportation && chemin != m_chemin) {
                m_chemin = chemin;
                emit cheminChanged();
            }
            emit enregistre(ok, chemin);
        }, Qt::QueuedConnection);
    });
}

// ——— Presse-papiers ———

void DocumentLO::copier(bool couper)
{
    if (!m_doc) return;
    commande(couper ? QStringLiteral(".uno:Cut") : QStringLiteral(".uno:Copy"));
    auto *doc = m_doc;
    // (la commande passe d'abord par le moteur ; le contenu est lu ensuite, dans le même ordre)
    Moteur::instance()->executer([this, doc] {
        QThread::msleep(60);
        const char *types[] = {"text/plain;charset=utf-8", "text/html", nullptr};
        size_t n = 0;
        char **typesSortie = nullptr, **contenus = nullptr;
        size_t *tailles = nullptr;
        QHash<QString, QByteArray> donnees;
        if (doc->pClass->getClipboard(doc, types, &n, &typesSortie, &tailles, &contenus)) {
            for (size_t i = 0; i < n; ++i) {
                if (contenus[i] && tailles[i]) donnees.insert(QString::fromUtf8(typesSortie[i]), QByteArray(contenus[i], int(tailles[i])));
                free(typesSortie[i]);
                free(contenus[i]);
            }
            free(typesSortie);
            free(contenus);
            free(tailles);
        }
        QMetaObject::invokeMethod(this, [donnees] {
            auto *mime = new QMimeData();
            for (auto it = donnees.begin(); it != donnees.end(); ++it) {
                if (it.key().startsWith(QLatin1String("text/plain"))) mime->setText(QString::fromUtf8(it.value()));
                else mime->setData(it.key(), it.value());
            }
            mime->setData(QString::fromLatin1(MIME_SAMA), QByteArray("1"));
            QGuiApplication::clipboard()->setMimeData(mime);
        }, Qt::QueuedConnection);
    });
}

void DocumentLO::coller(bool valeursSeules)
{
    if (!m_doc) return;
    const QMimeData *mime = QGuiApplication::clipboard()->mimeData();
    if (mime && !mime->hasFormat(QString::fromLatin1(MIME_SAMA))) {
        // Venu d'une autre application : le moteur reçoit le texte (et le HTML, qui garde un tableau)
        QList<QByteArray> types, contenus;
        if (mime->hasHtml()) {
            types << "text/html";
            contenus << mime->html().toUtf8();
        }
        if (mime->hasText()) {
            types << "text/plain;charset=utf-8";
            contenus << mime->text().toUtf8();
        }
        if (!types.isEmpty()) {
            auto *doc = m_doc;
            Moteur::instance()->executer([doc, types, contenus] {
                std::vector<const char *> t, c;
                std::vector<size_t> tailles;
                for (int i = 0; i < types.size(); ++i) {
                    t.push_back(types[i].constData());
                    c.push_back(contenus[i].constData());
                    tailles.push_back(size_t(contenus[i].size()));
                }
                doc->pClass->setClipboard(doc, t.size(), t.data(), tailles.data(), c.data());
            });
        }
    }
    commande(valeursSeules ? QStringLiteral(".uno:PasteOnlyValue") : QStringLiteral(".uno:Paste"));
}

// ——— Clavier et souris ———

void DocumentLO::touche(int code, int caractere)
{
    if (!m_doc) return;
    auto *doc = m_doc;
    Moteur::instance()->executer([doc, code, caractere] {
        doc->pClass->postKeyEvent(doc, LOK_KEYEVENT_KEYINPUT, caractere, code);
        doc->pClass->postKeyEvent(doc, LOK_KEYEVENT_KEYUP, caractere, code);
    });
}

void DocumentLO::keyPressEvent(QKeyEvent *e)
{
    if (!m_doc) {
        e->ignore();
        return;
    }
    // (Maj, Ctrl, Alt seules ne comptent pas)
    if (e->key() != Qt::Key_Shift && e->key() != Qt::Key_Control && e->key() != Qt::Key_Alt && e->key() != Qt::Key_Meta)
        emit clavierUtilise();
    const bool ctrl = e->modifiers() & Qt::ControlModifier;
    // Presse-papiers du système
    if (ctrl && !(e->modifiers() & Qt::AltModifier)) {
        if (e->key() == Qt::Key_C) { copier(false); return; }
        if (e->key() == Qt::Key_X) { copier(true); return; }
        if (e->key() == Qt::Key_V) { coller(e->modifiers() & Qt::ShiftModifier); return; }
    }
    // Annuler, rétablir : en « réparation », pour que les changements faits par les macros de Sama (tableaux…)
    // s'annulent aussi (sinon le moteur les refuse, comme venant d'une autre vue). Pendant la saisie dans une case,
    // la touche va au moteur.
    if (ctrl && !(e->modifiers() & Qt::AltModifier) && !m_curseurTexteVisible && (e->key() == Qt::Key_Z || e->key() == Qt::Key_Y)) {
        const bool refaire = e->key() == Qt::Key_Y || (e->modifiers() & Qt::ShiftModifier);
        commande(refaire ? QStringLiteral(".uno:Redo") : QStringLiteral(".uno:Undo"),
                 {{QStringLiteral("Repair"), QVariantMap{{QStringLiteral("type"), QStringLiteral("boolean")}, {QStringLiteral("value"), true}}}});
        return;
    }
    const QString texte = e->text();
    const int code = codeTouche(e->key());
    // Texte (lettres, chiffres, ɛ ɔ ɲ ŋ…) : caractère par caractère. Pas sur un graphique choisi (le moteur y
    // ajouterait un texte sans qu'on le veuille), comme dans Excel
    if (!texte.isEmpty() && texte.at(0).isPrint() && !(ctrl && !(e->modifiers() & Qt::AltModifier))) {
        if (!m_objetTwips.isNull() && !m_objetActif) return;
        auto *doc = m_doc;
        const QList<uint> points = texte.toUcs4();
        Moteur::instance()->executer([doc, points] {
            for (uint p : points) {
                doc->pClass->postKeyEvent(doc, LOK_KEYEVENT_KEYINPUT, int(p), 0);
                doc->pClass->postKeyEvent(doc, LOK_KEYEVENT_KEYUP, int(p), 0);
            }
        });
        return;
    }
    if (code) {
        const int c = code | modificateurs(e->modifiers());
        const int caractere = code == K_RETURN ? 13 : code == K_TAB ? 9 : code == K_BACKSPACE ? 8 : code == K_ESCAPE ? 27 : 0;
        touche(c, caractere);
        return;
    }
    e->ignore();
}

void DocumentLO::keyReleaseEvent(QKeyEvent *e)
{
    e->accept();
}

void DocumentLO::inputMethodEvent(QInputMethodEvent *e)
{
    if (!m_doc || e->commitString().isEmpty()) return;
    auto *doc = m_doc;
    const QByteArray texte = e->commitString().toUtf8();
    Moteur::instance()->executer([doc, texte] {
        doc->pClass->postWindowExtTextInputEvent(doc, 0, LOK_EXT_TEXTINPUT, texte.constData());
        doc->pClass->postWindowExtTextInputEvent(doc, 0, LOK_EXT_TEXTINPUT_END, texte.constData());
    });
}

QVariant DocumentLO::inputMethodQuery(Qt::InputMethodQuery requete) const
{
    if (requete == Qt::ImEnabled) return true;
    if (requete == Qt::ImCursorRectangle) return curseurTexte().translated(-m_vueX, -m_vueY);
    return QQuickItem::inputMethodQuery(requete);
}

void DocumentLO::envoyerSouris(int type, QPointF p, int nombre, int boutons, int mod)
{
    if (!m_doc) return;
    const qreal f = TWIPS_PAR_PIXEL / m_zoom;
    const int x = qRound(p.x() * f), y = qRound(p.y() * f);
    auto *doc = m_doc;
    Moteur::instance()->executer([doc, type, x, y, nombre, boutons, mod] {
        doc->pClass->postMouseEvent(doc, type, x, y, nombre, boutons, mod);
    });
}

void DocumentLO::souris(int type, QMouseEvent *e, int nombre)
{
    int boutons = 0;
    const Qt::MouseButtons b = type == LOK_MOUSEEVENT_MOUSEBUTTONUP ? Qt::MouseButtons(e->button()) : e->buttons();
    if (b & Qt::LeftButton) boutons |= 1;
    if (b & Qt::MiddleButton) boutons |= 2;
    if (b & Qt::RightButton) boutons |= 4;
    // (m_decalage : une poignée d'objet prise un peu à côté est donnée au moteur à sa place exacte)
    envoyerSouris(type, e->position() + QPointF(m_vueX, m_vueY) + m_decalage, nombre, boutons, modificateurs(e->modifiers()));
}

void DocumentLO::mousePressEvent(QMouseEvent *e)
{
    forceActiveFocus(Qt::MouseFocusReason);
    // Objet choisi : une poignée (pour l'agrandir) ou l'objet lui-même (pour le déplacer) ? Le moteur fait le reste
    m_poignee = -1;
    m_decalage = QPointF();
    const QRectF o = objet();
    if (e->button() == Qt::LeftButton && !o.isNull() && !m_objetActif) {
        const QPointF p = e->position() + QPointF(m_vueX, m_vueY);
        const QList<QPointF> points = poignees(o);
        for (int i = 0; i < points.size() && m_poignee < 0; ++i) {
            if (QLineF(p, points[i]).length() <= 7) {
                m_poignee = i;
                m_decalage = points[i] - p;
            }
        }
        if (m_poignee < 0 && o.contains(p)) m_poignee = 8;
        if (m_poignee >= 0) emit objetTenuChanged();
    }
    souris(LOK_MOUSEEVENT_MOUSEBUTTONDOWN, e, 1);
    e->accept();
    emit pointeurAppuye();
}

void DocumentLO::mouseMoveEvent(QMouseEvent *e)
{
    souris(LOK_MOUSEEVENT_MOUSEMOVE, e, 1);
}

void DocumentLO::mouseReleaseEvent(QMouseEvent *e)
{
    souris(LOK_MOUSEEVENT_MOUSEBUTTONUP, e, 1);
    m_decalage = QPointF();
    if (m_poignee >= 0) {
        m_poignee = -1;
        emit objetTenuChanged();
    }
    if (e->button() == Qt::LeftButton) emit pointeurRelache(e->position().x(), e->position().y());
}

void DocumentLO::mouseDoubleClickEvent(QMouseEvent *e)
{
    emit doubleClique();
    // (pas de modification d'un graphique dans le moteur : ses fenêtres ne seraient pas montrées)
    if (!m_objetTwips.isNull() && objet().contains(e->position() + QPointF(m_vueX, m_vueY))) return;
    souris(LOK_MOUSEEVENT_MOUSEBUTTONDOWN, e, 2);
    souris(LOK_MOUSEEVENT_MOUSEBUTTONUP, e, 2);
}

// Survol : le moteur choisit la forme du pointeur (déplacer un graphique, agrandir…). Un seul survol en route à la fois
void DocumentLO::hoverMoveEvent(QHoverEvent *e)
{
    m_survolSuivant = e->position() + QPointF(m_vueX, m_vueY);
    emit survole(e->position().x(), e->position().y());
    if (m_survolEnvoye) m_survolAttend = true;
    else survoler();
}

void DocumentLO::survoler()
{
    if (!m_doc) return;
    m_survolEnvoye = true;
    m_survolAttend = false;
    const qreal f = TWIPS_PAR_PIXEL / m_zoom;
    const int x = qRound(m_survolSuivant.x() * f), y = qRound(m_survolSuivant.y() * f);
    auto *doc = m_doc;
    Moteur::instance()->executer([this, doc, x, y] {
        doc->pClass->postMouseEvent(doc, LOK_MOUSEEVENT_MOUSEMOVE, x, y, 1, 0, 0);
        QMetaObject::invokeMethod(this, [this] {
            m_survolEnvoye = false;
            if (m_survolAttend) survoler();
        }, Qt::QueuedConnection);
    });
}

void DocumentLO::wheelEvent(QWheelEvent *e)
{
    // Ctrl + molette : zoom ; sinon défilement (Maj : de côté)
    const QPoint angle = e->angleDelta();
    if (e->modifiers() & Qt::ControlModifier) {
        setZoom(m_zoom * (angle.y() > 0 ? 1.1 : 1 / 1.1));
        return;
    }
    QPointF pas = e->pixelDelta().isNull() ? QPointF(angle) / 120.0 * 60.0 : QPointF(e->pixelDelta());
    if (e->modifiers() & Qt::ShiftModifier) pas = QPointF(pas.y(), pas.x());
    if (!qFuzzyIsNull(pas.y())) setVueY(m_vueY - pas.y());
    if (!qFuzzyIsNull(pas.x())) setVueX(m_vueX - pas.x());
}
