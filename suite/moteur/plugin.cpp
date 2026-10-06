// Module QML « Sama.Moteur » : import Sama.Moteur, puis DocumentLO { }
#include "document.h"

#include <QQmlEngineExtensionPlugin>
#include <qqml.h>

class MoteurPlugin : public QQmlExtensionPlugin
{
    Q_OBJECT
    Q_PLUGIN_METADATA(IID QQmlExtensionInterface_iid)
public:
    void registerTypes(const char *uri) override
    {
        qmlRegisterType<DocumentLO>(uri, 1, 0, "DocumentLO");
    }
};

#include "plugin.moc"
