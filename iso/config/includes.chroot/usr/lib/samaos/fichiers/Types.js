// Familles de fichiers (comme /usr/libexec/samaos/fichiers.py) : type affiché, couleur de l'étiquette, tuile Sama
.pragma library

var familles = {
    texte: { type: "Document", teinte: "#3D5A99", tuile: "docs" },
    tableur: { type: "Tableur", teinte: "#2F6B57", tuile: "sheet" },
    presentation: { type: "Présentation", teinte: "#C2652E", tuile: "presentations" },
    pdf: { type: "Document PDF", teinte: "#A3322A", tuile: "pdf" },
    image: { type: "Image", teinte: "#7A5C99", tuile: "photos" },
    audio: { type: "Audio", teinte: "#8A6A4A", tuile: "" },
    video: { type: "Vidéo", teinte: "#1E2740", tuile: "" },
    archive: { type: "Archive", teinte: "#8A6A4A", tuile: "archives" },
    autre: { type: "Fichier", teinte: "#8A8277", tuile: "" },
    dossier: { type: "Dossier", teinte: "#C99A5B", tuile: "" }
}
var extensions = {
    texte: "doc docx odt rtf txt md pages wpd",
    tableur: "xls xlsx ods csv numbers",
    presentation: "ppt pptx odp key",
    pdf: "pdf",
    image: "jpg jpeg png gif webp bmp svg heic tif tiff",
    audio: "mp3 ogg oga wav flac m4a aac opus",
    video: "mp4 mkv webm avi mov m4v 3gp",
    archive: "zip tar gz bz2 xz 7z rar tgz zst"
}

function extension(nom) {
    var i = String(nom).lastIndexOf(".")
    return i > 0 ? String(nom).substring(i + 1).toLowerCase() : ""
}
function famille(nom, estDossier) {
    if (estDossier) return "dossier"
    var e = extension(nom)
    for (var f in extensions) if ((" " + extensions[f] + " ").indexOf(" " + e + " ") >= 0) return f
    return "autre"
}
function filtres(nomFiltre) {
    var l = []
    var familles_ = nomFiltre === "documents" ? ["texte", "tableur", "presentation", "pdf"] : nomFiltre === "images" ? ["image"] : []
    familles_.forEach(function (f) { extensions[f].split(" ").forEach(function (e) { l.push("*." + e); l.push("*." + e.toUpperCase()) }) })
    return l
}

// « 96 Ko », « 3,5 Mo », « 1,2 Go »
function taille(octets) {
    if (octets >= 1073741824) return (octets / 1073741824).toLocaleString(Qt.locale(), "f", 1).replace(/[,.]0$/, "") + " Go"
    if (octets >= 1048576) return (octets / 1048576).toLocaleString(Qt.locale(), "f", 1).replace(/[,.]0$/, "") + " Mo"
    if (octets >= 1024) return Math.round(octets / 1024) + " Ko"
    return octets + " octet" + (octets > 1 ? "s" : "")
}
// « Aujourd'hui, 10:42 », « Hier, 18:20 », « 30 sept., 16:05 », « 12 mars 2024 »
function date(d) {
    if (!d) return ""
    d = new Date(d)
    var minuit = new Date(); minuit.setHours(0, 0, 0, 0)
    var jour = new Date(d.getTime()); jour.setHours(0, 0, 0, 0)
    var ecart = Math.round((minuit.getTime() - jour.getTime()) / 86400000)
    var heure = d.toLocaleTimeString(Qt.locale(), "HH:mm")
    if (ecart === 0) return "Aujourd'hui, " + heure
    if (ecart === 1) return "Hier, " + heure
    if (d.getFullYear() === new Date().getFullYear()) return d.toLocaleDateString(Qt.locale(), "d MMM") + ", " + heure
    return d.toLocaleDateString(Qt.locale(), "d MMMM yyyy")
}
