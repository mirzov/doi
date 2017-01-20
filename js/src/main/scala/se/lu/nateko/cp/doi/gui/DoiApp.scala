package se.lu.nateko.cp.doi.gui

import scala.scalajs.js.JSApp
import org.scalajs.dom.document
import se.lu.nateko.cp.doi.gui.views.MainView

object DoiApp extends JSApp {

	val initState = DoiState(
		prefix = "10.5072",//test value; the actual one is fetched from backend
		dois = Nil,
		info = Map.empty,
		selected = None,
		ioState = IoState(None, None),
		alreadyExists = None,
		error = None
	)
	val store = new DoiRedux.Store(DoiReducer.reducer, initState)

	val mainView = new MainView(store)
	val renderer = new Renderer(mainView)
	store.subscribe(renderer)

	def main(): Unit = {
		val mainDiv = document.getElementById("main")
		mainDiv.parentNode.replaceChild(mainView.element.render, mainDiv)

		store.dispatch(ThunkActions.FetchDoiPrefix)
		store.dispatch(ThunkActions.DoiListRefreshRequest)
	}

}
