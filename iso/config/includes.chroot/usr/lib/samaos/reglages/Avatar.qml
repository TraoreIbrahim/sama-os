// Pastille de compte : la photo de la personne, sinon ses initiales sur une couleur qui lui est propre.
import QtQuick
import org.kde.kirigami as Kirigami

Rectangle {
    id: avatar
    property string nom
    property string photo           // chemin ou adresse de l'image ; vide : initiales
    property int uid: 0
    property bool moi: false
    readonly property var teintes: ["#2F6B57", "#3D5A99", "#8A5A2B", "#6B4C9A", "#2E7D8C"]

    implicitWidth: 36
    implicitHeight: 36
    radius: width / 2
    color: moi ? Couleurs.laterite : teintes[uid % teintes.length]
    clip: true

    Text {
        anchors.centerIn: parent
        visible: image.status !== Image.Ready
        text: {
            var mots = avatar.nom.trim().split(/\s+/).filter(function (m) { return m })
            return mots.length === 0 ? "" : (mots[0].charAt(0) + (mots.length > 1 ? mots[mots.length - 1].charAt(0) : "")).toUpperCase()
        }
        font.pixelSize: Math.round(avatar.width * 0.36)
        font.weight: Font.DemiBold
        color: "#FFFFFF"
    }
    Kirigami.ShadowedImage {
        id: image
        anchors.fill: parent
        radius: width / 2   // photo découpée en rond
        source: avatar.photo === "" ? "" : (avatar.photo.indexOf(":/") > 0 ? avatar.photo : "file://" + avatar.photo)
        fillMode: Image.PreserveAspectCrop
        sourceSize.width: avatar.width * 2
        sourceSize.height: avatar.height * 2
        visible: status === Image.Ready
    }
}
