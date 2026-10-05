#!/usr/bin/env python3
"""Agent d'autorisation de Sama OS (remplace celui de KDE, dont la fenêtre ne peut pas être habillée).

Quand une action demande le mot de passe d'un administrateur (créer un compte, ajouter une imprimante…),
le système (polkit) s'adresse à cet agent. Il ouvre la fenêtre Sama « Autorisation requise »
(/usr/lib/samaos/autorisation/Autorisation.qml), reçoit le mot de passe par le tube privé qui le relie à la
fenêtre, et le transmet à polkit. Mot de passe incorrect : la fenêtre revient avec un message.
Lancé par le service de session plasma-polkit-agent (voir /etc/systemd/user/plasma-polkit-agent.service.d).
"""
import grp
import json
import locale
import os
import pwd

import gi

gi.require_version("Polkit", "1.0")
gi.require_version("PolkitAgent", "1.0")
from gi.repository import Gio, GLib, Polkit, PolkitAgent  # noqa: E402  (PolkitAgent : vérification du mot de passe)

FENETRE = ["/usr/lib/samaos/bin/samaos-reglages", "/usr/lib/samaos/autorisation/Autorisation.qml", "--"]
CHEMIN_AGENT = "/org/samaos/AgentAutorisation"

# Applications qui demandent souvent une autorisation : (nom, pluriel)
APPLICATIONS = {"samaos-reglages": ("Les Réglages", True), "sugu": ("Sugu", False), "plasma-discover": ("Sugu", False),
                "dolphin": ("Fichiers", False), "konsole": ("Le Terminal", False), "plasmashell": ("Sama", False)}

# Ce que demande chaque action, à la manière de la maquette (« Sugu veut installer… ») ;
# les messages de polkit ne sont pas traduits en français.
ACTIONS = [
    ("org.freedesktop.accounts.user-administration", "gérer les comptes de l'ordinateur"),
    ("org.freedesktop.accounts.change-own-password", "changer votre mot de passe"),
    ("org.freedesktop.accounts.change-own-user-data", "modifier votre compte"),
    ("org.freedesktop.accounts.set-login-option", "changer les options de connexion"),
    ("org.freedesktop.NetworkManager.", "modifier les réglages du réseau"),
    ("org.freedesktop.timedate1.", "changer la date, l'heure ou le fuseau horaire"),
    ("org.freedesktop.hostname1.", "changer le nom de l'ordinateur"),
    ("org.freedesktop.locale1.", "changer la langue ou le clavier de l'ordinateur"),
    ("org.freedesktop.login1.", "changer l'alimentation de l'ordinateur"),
    ("org.opensuse.cupspkhelper.", "gérer les imprimantes"),
    ("org.freedesktop.packagekit.", "installer ou mettre à jour des logiciels pour tous les utilisateurs"),
    ("org.freedesktop.Flatpak.", "installer ou mettre à jour des logiciels pour tous les utilisateurs"),
    ("org.freedesktop.udisks2.", "gérer les disques"),
    ("org.bluez.", "gérer le Bluetooth"),
]


def compte(identite):
    """Nom affiché, rôle, photo et initiales d'une identité polkit (utilisateur)."""
    uid = identite.get_uid() if isinstance(identite, Polkit.UnixUser) else os.getuid()
    p = pwd.getpwuid(uid)
    nom = (p.pw_gecos.split(",")[0] or p.pw_name).strip()
    admins = set()
    for groupe in grp.getgrall():
        if groupe.gr_name in ("sudo", "admin", "wheel"):
            admins.update(groupe.gr_mem)
    photo = ""
    for chemin in ("/var/lib/AccountsService/icons/" + p.pw_name, os.path.join(p.pw_dir, ".face.icon")):
        if os.path.isfile(chemin):
            photo = chemin
            break
    mots = nom.split()
    initiales = (mots[0][0] + (mots[-1][0] if len(mots) > 1 else "")).upper() if mots else "?"
    return {"nom": nom, "role": "administrateur" if p.pw_name in admins else "", "photo": photo, "initiales": initiales}


def demandeur(details):
    """Application à l'origine de la demande : on remonte les processus parents depuis celui qui a appelé
    polkit (souvent un intermédiaire comme pkexec, busctl ou un script) jusqu'à une application connue."""
    pid = details.get("polkit.caller-pid") or details.get("polkit.subject-pid") if details else None
    for _ in range(8):
        if not pid or str(pid) in ("0", "1"):
            break
        try:
            nom = open("/proc/%s/comm" % pid).read().strip()
            if nom in APPLICATIONS:
                return APPLICATIONS[nom]
            pid = open("/proc/%s/stat" % pid).read().rsplit(")", 1)[1].split()[1]   # processus parent
        except (OSError, IndexError):
            break
    return None


def phrase(action, message, details, application):
    """« Les Réglages veulent gérer les comptes de l'ordinateur. » ; à défaut, le message de polkit."""
    sujet, pluriel = application or ("Une application", False)
    verbe = "veulent" if pluriel else "veut"
    if action == "org.freedesktop.policykit.exec":
        # (polkit ne transmet pas toujours le nom du programme lancé par pkexec)
        programme = os.path.basename(details.get("program", "")) if details else ""
        if not programme or programme in ("tee", "rm", "sh", "bash", "lpadmin", "cp", "mkdir"):
            return "%s %s modifier un réglage de l'ordinateur." % (sujet, verbe)
        return "%s %s lancer « %s » en tant qu'administrateur." % (sujet, verbe, programme)
    for prefixe, quoi in ACTIONS:
        if action.startswith(prefixe):
            return "%s %s %s." % (sujet, verbe, quoi)
    return message


class Demande:
    """Une demande d'autorisation : fenêtre, essais successifs, réponse à polkit."""

    def __init__(self, invocation, action, message, details, cookie, identites):
        self.invocation, self.cookie = invocation, cookie
        self.processus = None
        self.session = None
        # Identités proposées par polkit (administrateurs) : l'utilisateur connecté s'il en fait partie
        uids = [int(d["uid"]) for genre, d in identites if genre == "unix-user" and "uid" in d]
        uid = os.getuid() if os.getuid() in uids else (uids[0] if uids else os.getuid())
        self.identite = Polkit.UnixUser.new(uid)
        infos_detail = dict(details or {})
        application = demandeur(infos_detail)
        message = phrase(action, message, infos_detail, application)
        lignes = ["Action : " + action] + (["Demandée par : " + application[0]] if application else [])
        lignes += ["%s : %s" % (k, v) for k, v in infos_detail.items() if not k.startswith("polkit.")]
        message = (message or "").strip()
        if message and message[-1] not in ".!?":
            message += "."
        self.infos = dict(compte(self.identite), message=message, details="\n".join(lignes), erreur="")
        self.ouvrir_fenetre()

    def ouvrir_fenetre(self):
        lanceur = Gio.SubprocessLauncher.new(Gio.SubprocessFlags.STDERR_PIPE | Gio.SubprocessFlags.STDOUT_SILENCE)
        # Messages de la fenêtre sur sa sortie d'erreur uniquement (jamais dans le journal du système)
        lanceur.setenv("QT_FORCE_STDERR_LOGGING", "1", True)
        lanceur.setenv("QT_MESSAGE_PATTERN", "%{message}", True)
        lanceur.setenv("QT_LOGGING_RULES", "qml.warning=true", True)
        self.processus = lanceur.spawnv(FENETRE + [json.dumps(self.infos)])
        self.processus.communicate_utf8_async(None, None, self.fenetre_fermee)

    def fenetre_fermee(self, processus, resultat):
        try:
            _, _, sortie = processus.communicate_utf8_finish(resultat)
        except GLib.Error:
            sortie = ""
        self.processus = None
        mot_de_passe = None
        for ligne in (sortie or "").splitlines():
            if ligne.startswith("SAMA_MDP="):
                mot_de_passe = ligne[len("SAMA_MDP="):]
        if mot_de_passe is None:
            return self.finir(False)
        session = PolkitAgent.Session.new(self.identite, self.cookie)
        session.connect("request", lambda s, invite, echo: s.response(mot_de_passe))
        session.connect("completed", self.session_terminee)
        self.session = session
        session.initiate()

    def session_terminee(self, session, autorise):
        self.session = None
        if autorise:
            return self.finir(True)
        self.infos["erreur"] = "Mot de passe incorrect. Réessayez."
        self.ouvrir_fenetre()

    def annuler(self):
        """Polkit retire la demande (programme demandeur fermé…) : on ferme la fenêtre."""
        if self.session:
            self.session.cancel()
        if self.processus:
            self.processus.force_exit()
        self.finir(False)

    def finir(self, autorise):
        DEMANDES.pop(self.cookie, None)
        if self.invocation is None:
            return
        if autorise:
            self.invocation.return_value(None)
        else:
            self.invocation.return_dbus_error("org.freedesktop.PolicyKit1.Error.Cancelled", "Autorisation annulée")
        self.invocation = None


# Interface D-Bus d'un agent d'autorisation (appelée par polkit)
INTERFACE = """
<node>
  <interface name="org.freedesktop.PolicyKit1.AuthenticationAgent">
    <method name="BeginAuthentication">
      <arg type="s" name="action_id" direction="in"/>
      <arg type="s" name="message" direction="in"/>
      <arg type="s" name="icon_name" direction="in"/>
      <arg type="a{ss}" name="details" direction="in"/>
      <arg type="s" name="cookie" direction="in"/>
      <arg type="a(sa{sv})" name="identities" direction="in"/>
    </method>
    <method name="CancelAuthentication">
      <arg type="s" name="cookie" direction="in"/>
    </method>
  </interface>
</node>
"""
DEMANDES = {}


def appel(connexion, expediteur, chemin, interface, methode, parametres, invocation):
    if methode == "BeginAuthentication":
        action, message, _icone, details, cookie, identites = parametres.unpack()
        DEMANDES[cookie] = Demande(invocation, action, message, details, cookie, identites)
    elif methode == "CancelAuthentication":
        (cookie,) = parametres.unpack()
        demande = DEMANDES.get(cookie)
        if demande:
            demande.annuler()
        invocation.return_value(None)


def main():
    locale.setlocale(locale.LC_ALL, "")   # messages de polkit dans la langue de la session
    bus = Gio.bus_get_sync(Gio.BusType.SYSTEM, None)
    noeud = Gio.DBusNodeInfo.new_for_xml(INTERFACE)
    bus.register_object(CHEMIN_AGENT, noeud.interfaces[0], appel, None, None)
    # Session graphique : celle du service (XDG_SESSION_ID), sinon la session d'affichage de l'utilisateur
    session = os.environ.get("XDG_SESSION_ID")
    if not session:
        import subprocess
        session = subprocess.run(["loginctl", "show-user", str(os.getuid()), "-p", "Display", "--value"],
                                 capture_output=True, text=True).stdout.strip()
    sujet = Polkit.UnixSession.new(session) if session else Polkit.UnixSession.new_for_process_sync(os.getpid(), None)
    langue = os.environ.get("LC_MESSAGES") or os.environ.get("LANG") or "fr_FR.UTF-8"
    Polkit.Authority.get_sync(None).register_authentication_agent_sync(sujet, langue, CHEMIN_AGENT, None)
    GLib.MainLoop().run()


if __name__ == "__main__":
    main()
