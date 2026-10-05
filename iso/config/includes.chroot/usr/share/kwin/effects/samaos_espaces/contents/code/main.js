// Transition entre les Espaces de Sama (les « activités » de Plasma).
// Les fenêtres de l'Espace choisi avancent légèrement en apparaissant (mouvement en profondeur : il ne dépend
// pas de l'ordre des Espaces). Celles de l'Espace quitté sont masquées par KWin avant que l'effet ne les voie :
// leur sortie n'est animée que si KWin les peint encore.
"use strict";

var espaces = {
    sortie: animationTime(200),
    entree: animationTime(300),
    precedent: effects.currentActivity,

    loadConfig: function () {
        espaces.sortie = animationTime(200);
        espaces.entree = animationTime(300);
    },

    annuler: function (w) {
        if (w.sama) {
            cancel(w.sama);
            delete w.sama;
        }
    },

    sortir: function (w) {
        espaces.annuler(w);
        w.sama = animate({
            window: w,
            duration: espaces.sortie,
            curve: QEasingCurve.InCubic,
            keepAlive: false,
            animations: [
                { type: Effect.Opacity, from: 1.0, to: 0.0 },
                { type: Effect.Scale, from: 1.0, to: 0.96 }
            ]
        });
    },

    entrer: function (w) {
        espaces.annuler(w);
        w.sama = animate({
            window: w,
            duration: espaces.entree,
            curve: QEasingCurve.OutCubic,
            keepAlive: false,
            animations: [
                { type: Effect.Opacity, from: 0.0, to: 1.0 },
                { type: Effect.Scale, from: 0.96, to: 1.0 }
            ]
        });
    },

    changement: function (nouveau) {
        var ancien = espaces.precedent;
        espaces.precedent = nouveau;
        if (!ancien || ancien === nouveau) return;
        if (effects.hasActiveFullScreenEffect) return;   // vue d'ensemble des Espaces ouverte : elle anime déjà

        var fenetres = effects.stackingOrder;
        for (var i = 0; i < fenetres.length; ++i) {
            var w = fenetres[i];
            // Seulement les vraies fenêtres d'applications (pas le bureau, la Natte, les bulles…)
            if (!w.normalWindow && !w.dialog) continue;
            if (w.minimized || !w.isOnDesktop(effects.currentDesktop)) continue;
            var avant = w.isOnActivity(ancien);
            var apres = w.isOnActivity(nouveau);
            if (avant === apres) continue;              // présente dans les deux (ou aucun) : rien à faire
            if (avant) espaces.sortir(w); else espaces.entrer(w);
        }
    },

    init: function () {
        effect.configChanged.connect(espaces.loadConfig);
        effects.currentActivityChanged.connect(espaces.changement);
    }
};

espaces.init();
