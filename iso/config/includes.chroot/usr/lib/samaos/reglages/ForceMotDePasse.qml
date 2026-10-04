// Évalue la solidité d'un mot de passe (indication simple, pour aider à choisir).
pragma Singleton
import QtQuick

QtObject {
    function score(m) {
        var s = 0
        if (m.length >= 8) s++
        if (m.length >= 12) s++
        if (/[a-z]/.test(m) && /[A-Z]/.test(m)) s++
        if (/[0-9]/.test(m)) s++
        if (/[^A-Za-z0-9]/.test(m)) s++
        return m.length < 8 ? 0 : s
    }
    function libelle(m) {
        var s = score(m)
        return s === 0 ? "Trop court : au moins 8 caractères" : s <= 2 ? "Faible : ajoutez des chiffres ou des majuscules" : s <= 3 ? "Correct" : "Solide"
    }
}
