# Module QML Sama.Moteur : le moteur de LibreOffice pour Sama Sheet et Sama Docs.
# Construction : qmake6 && make ; installation : make install (dans /usr/lib/samaos/qml/Sama/Moteur).
# En-têtes de LibreOfficeKit : paquet libreofficekit-dev, ou « qmake6 LOK_INCLUDE=dossier ».
TEMPLATE = lib
TARGET = samamoteur
CONFIG += plugin c++17 release
QT += quick
HEADERS += moteur.h document.h
SOURCES += moteur.cpp document.cpp plugin.cpp
!isEmpty(LOK_INCLUDE): INCLUDEPATH += $$LOK_INCLUDE
LIBS += -ldl
DESTDIR = Sama/Moteur
cible.path = /usr/lib/samaos/qml/Sama/Moteur
cible.files = Sama/Moteur/libsamamoteur.so qmldir
cible.CONFIG += no_check_exist      # (la bibliothèque n'existe qu'après la construction)
INSTALLS += cible
QMAKE_POST_LINK = cp $$PWD/qmldir Sama/Moteur/qmldir
