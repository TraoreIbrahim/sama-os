// Aide pendant la saisie d'une formule : la fonction ouverte (ses arguments, l'argument en cours en gras, ce qu'elle
// fait, un exemple), son résultat en direct (maquette « La grille libre » : « Résultat 16 000 F »), et des propositions
// quand on commence à écrire un nom (Tab ou Entrée pour choisir).
import QtQuick
import QtQuick.Layouts
import "../reglages"
import "Fonctions.js" as Fonctions

Rectangle {
    id: aide
    property string texte: ""          // la formule, jusqu'au curseur
    property bool completer: false     // propositions (dans la barre de formule)
    property int choisie: 0
    readonly property var analyse: Fonctions.analyser(texte)
    readonly property bool proposer: completer && analyse.propositions.length > 0
    readonly property var fonction: analyse.fonction
    signal proposition(string nom, string prefixe)

    // ——— Le résultat en direct ———
    // Le moteur calcule la formule dans la case même (SamaFormules.Evaluer : le classeur n'en garde rien), un instant
    // après la dernière touche ; jusque-là, le résultat d'avant reste, pâli.
    property string formule: ""        // toute la formule
    property int feuille: -1
    property int colonne: -1
    property int ligne: -1
    property var resultat: null        // { texte, erreur, nombre }
    property bool resultatAJour: false
    property int demande: 0
    onFormuleChanged: {
        resultatAJour = false
        if (!formule) resultat = null
        calculer.restart()
    }
    Timer { id: calculer; interval: 350; onTriggered: aide.evaluer() }
    function evaluer() {
        var f = formule
        if (f.length < 2 || f.charAt(0) !== "=" || feuille < 0 || colonne < 0 || ligne < 0) { resultat = null; return }
        // (une formule qui irait sur Internet n'est pas calculée à chaque touche)
        if (/SERVICEWEB|WEBSERVICE/i.test(f)) { resultat = null; return }
        // (un signe au bout : la suite arrive)
        if (/[-+*\/^&;(=<>:]\s*$/.test(f)) return
        var n = ++demande
        fenetre.tableaux.appeler("SamaFormules.Evaluer", [String(feuille), String(colonne), String(ligne), f], function (ok, v) {
            if (n !== aide.demande || !aide.formule) return
            var r = null
            try { if (ok && v && v.charAt(0) !== "!") r = JSON.parse(v) } catch (e) {}
            aide.resultat = r
            aide.resultatAJour = r !== null
        })
    }
    // Les erreurs du moteur, en mots
    function motErreur(code) {
        if (code === 532) return "division par zéro"
        if (code === 525) return "un nom que Sama ne connaît pas"
        if (code === 519) return "une valeur qui n'est pas un nombre"
        if (code === 524) return "une case qui n'existe pas"
        if (code === 522 || code === 523) return "la formule utilise sa propre case"
        if (code === 503) return "un calcul impossible"
        if (code === 32767) return "rien de trouvé"
        if (code >= 501 && code <= 511) return "formule pas encore complète"
        return "erreur"
    }
    readonly property bool resultatMontre: resultat !== null && formule.length > 1
    function accepter() { if (proposer) proposition(analyse.propositions[choisie].nom, analyse.prefixe) }
    function suivante() { choisie = Math.min(choisie + 1, analyse.propositions.length - 1) }
    function precedente() { choisie = Math.max(choisie - 1, 0) }
    onAnalyseChanged: choisie = 0
    // Le nom de la fonction dans Excel (le fichier .xlsx l'enregistre ainsi ; Excel en anglais l'affiche ainsi)
    readonly property var noms: ({ "SOMME": "SUM", "MOYENNE": "AVERAGE", "NB": "COUNT", "NBVAL": "COUNTA", "SI": "IF", "ET": "AND", "OU": "OR",
                                   "ARRONDI": "ROUND", "ARRONDI.SUP": "ROUNDUP", "ARRONDI.INF": "ROUNDDOWN", "SOMME.SI": "SUMIF", "NB.SI": "COUNTIF",
                                   "MOYENNE.SI": "AVERAGEIF", "RECHERCHEV": "VLOOKUP", "SOMMEPROD": "SUMPRODUCT", "PRODUIT": "PRODUCT", "ENT": "INT",
                                   "RANG": "RANK", "AUJOURDHUI": "TODAY", "MAINTENANT": "NOW", "ANNEE": "YEAR", "MOIS": "MONTH", "JOUR": "DAY",
                                   "CONCATENER": "CONCATENATE", "GAUCHE": "LEFT", "DROITE": "RIGHT", "NBCAR": "LEN", "MAJUSCULE": "UPPER",
                                   "MINUSCULE": "LOWER", "SIERREUR": "IFERROR" })

    // Signature : SOMME(<b>nombre1</b> ; [nombre2] ; …)
    function signature(f, n) {
        if (!f) return ""
        var args = f.args.slice()
        var i = Math.min(n, args.length - 1)
        if (i >= 0 && args[i] === "…") i--
        return f.nom + "(" + args.map(function (a, k) { return k === i ? "<b>" + a + "</b>" : a }).join(" ; ") + ")"
    }

    visible: proposer || fonction !== null || resultatMontre
    width: 400
    height: colonne.implicitHeight + 24
    radius: 12
    color: Couleurs.champ
    border.width: 0.5
    border.color: Couleurs.bord
    Rectangle { anchors.fill: parent; anchors.topMargin: 4; anchors.margins: -1; radius: 13; z: -1; color: Qt.rgba(40 / 255, 30 / 255, 20 / 255, 0.1) }

    ColumnLayout {
        id: colonne
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.top: parent.top
        anchors.margins: 12
        spacing: 6

        // Propositions
        Repeater {
            model: aide.proposer ? aide.analyse.propositions : []
            delegate: Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: 30
                radius: 7
                color: index === aide.choisie ? fenetre.accentFond : zone.containsMouse ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05) : "transparent"
                RowLayout {
                    anchors.fill: parent
                    anchors.leftMargin: 8
                    anchors.rightMargin: 8
                    spacing: 10
                    Text { text: modelData.nom; font.pixelSize: 13; font.weight: Font.DemiBold; color: index === aide.choisie ? fenetre.accentEncre : Couleurs.texte }
                    Text { Layout.fillWidth: true; text: modelData.texte; font.pixelSize: 12; color: Couleurs.texte2; elide: Text.ElideRight }
                }
                MouseArea { id: zone; anchors.fill: parent; hoverEnabled: true; onClicked: { aide.choisie = index; aide.accepter() } }
            }
        }
        Text {
            visible: aide.proposer
            text: "Tab ou Entrée pour choisir"
            font.pixelSize: 11
            color: Couleurs.texte3
            Layout.leftMargin: 8
        }

        // La fonction en cours
        Text {
            visible: !aide.proposer && aide.fonction !== null
            Layout.fillWidth: true
            text: aide.signature(aide.fonction, aide.analyse.argument)
            textFormat: Text.StyledText
            font.pixelSize: 14
            color: Couleurs.texte
            wrapMode: Text.Wrap
        }
        Text {
            visible: !aide.proposer && aide.fonction !== null
            Layout.fillWidth: true
            text: aide.fonction ? aide.fonction.texte : ""
            font.pixelSize: 13
            color: Couleurs.texte2
            wrapMode: Text.Wrap
        }
        // Le résultat (le fond de Sheet ; une erreur, en mots, sur fond neutre)
        Rectangle {
            visible: !aide.proposer && aide.resultatMontre
            Layout.fillWidth: true
            Layout.preferredHeight: ligneResultat.implicitHeight + 16
            radius: 8
            color: aide.resultat && aide.resultat.erreur ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.2 : 0.05) : fenetre.accentFond
            opacity: aide.resultatAJour ? 1 : 0.55
            Behavior on opacity { NumberAnimation { duration: 120 } }
            RowLayout {
                id: ligneResultat
                anchors.left: parent.left
                anchors.right: parent.right
                anchors.verticalCenter: parent.verticalCenter
                anchors.leftMargin: 10
                anchors.rightMargin: 10
                spacing: 8
                Text {
                    Layout.alignment: Qt.AlignTop
                    Layout.topMargin: 2
                    text: "Résultat"
                    font.pixelSize: 12
                    color: aide.resultat && aide.resultat.erreur ? Couleurs.texte2 : fenetre.accentEncre
                }
                Text {
                    Layout.fillWidth: true
                    text: !aide.resultat ? "" : aide.resultat.erreur ? (aide.resultat.erreur >= 501 && aide.resultat.erreur <= 511 ? "" : aide.resultat.texte + " · ") + aide.motErreur(aide.resultat.erreur)
                          : aide.resultat.texte === "" ? "vide" : aide.resultat.texte
                    font.pixelSize: aide.resultat && aide.resultat.erreur ? 13 : 14
                    font.weight: aide.resultat && aide.resultat.erreur ? Font.Normal : Font.Bold
                    font.features: { "tnum": 1 }
                    color: aide.resultat && aide.resultat.erreur ? Couleurs.texte2 : fenetre.accentEncre
                    wrapMode: Text.Wrap
                    maximumLineCount: 2
                    elide: Text.ElideRight
                }
            }
        }
        Text {
            visible: !aide.proposer && aide.fonction !== null
            Layout.fillWidth: true
            text: aide.fonction ? "Exemple : " + aide.fonction.exemple : ""
            font.pixelSize: 12
            color: fenetre.accentEncre
            wrapMode: Text.Wrap
        }
        Text {
            visible: !aide.proposer && aide.fonction !== null
            Layout.fillWidth: true
            text: !aide.fonction ? "" : aide.noms[aide.fonction.nom] ? "Excel en anglais la lira comme " + aide.noms[aide.fonction.nom] + "."
                  : "Même nom dans Excel en anglais."
            font.pixelSize: 12
            color: Couleurs.texte3
            wrapMode: Text.Wrap
        }
        Text {
            visible: !aide.proposer && aide.fonction !== null
            text: "Voir l'aide des formules · F1"
            font.pixelSize: 12
            font.weight: Font.DemiBold
            color: fenetre.accentEncre
            MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: fenetre.ouvrirAide("sheet/formules") }
        }
    }
}
