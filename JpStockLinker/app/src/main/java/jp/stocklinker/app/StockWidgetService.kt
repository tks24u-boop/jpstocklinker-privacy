package jp.stocklinker.app

import android.content.Intent
import android.widget.RemoteViewsService

class StockWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return StockRemoteViewsFactory(this.applicationContext)
    }
}
