package com.minek.kotlin.everywhere.keuix.browser

import com.minek.kotlin.everywhere.keuix.browser.Snabbdom.h
import com.minek.kotlin.everywhere.keuix.browser.html.Attribute
import com.minek.kotlin.everywhere.keuix.browser.html.Html
import org.w3c.dom.Element
import org.w3c.dom.events.Event
import kotlin.browser.window

private fun <S> List<Attribute<S>>.toProps(receiver: (S) -> Unit): Triple<dynamic, dynamic, dynamic> {
    val props: dynamic = object {}
    val datasets: dynamic = object {}
    val on: dynamic = object {}

    this.forEach { attr ->
        when (attr) {
            is Attribute.DatasetProperty -> datasets[attr.name] = attr.value
            is Attribute.TextProperty -> props[attr.name] = attr.value
            is Attribute.BooleanProperty -> props[attr.name] = attr.value
            is Attribute.EventHandler -> on[attr.name] = { event: Event ->
                val msg = attr.value(event)
                if (msg != null) {
                    receiver(msg)
                }
            }
        }
    }

    return Triple(props, datasets, on)
}

private fun <S, P> tagVirtualNode(tagger: Html.Tagger<S, P>, receiver: (S) -> Unit): dynamic {
    return tagger.html.toVirtualNode { p -> receiver(tagger.tagger(p)) }
}

private fun <S> Html<S>.toVirtualNode(receiver: (S) -> Unit): dynamic {
    return when (this) {
        is Html.Text -> this.text
        is Html.Element -> {
            val data: dynamic = object {}
            val (props, datasets, on) = this.attributes.toProps(receiver)
            data["attrs"] = props
            data["dataset"] = datasets
            data["on"] = on
            h(
                    this.tagName,
                    data,
                    this.children
                            .map {
                                @Suppress("UnsafeCastFromDynamic")
                                it.toVirtualNode(receiver)
                            }
                            .toTypedArray()
            )
        }
        is Html.Tagger<S, *> -> tagVirtualNode(this, receiver)
    }
}

typealias Update<M, S> = (S, M) -> Pair<M, Cmd<S>?>
typealias View<M, S> = (M) -> Html<S>

class Program<M, S>(private val root: Element, init: M,
                    private val update: Update<M, S>, private val view: View<M, S>, onAfterRender: ((Element) -> Unit)?) {

    private var requestAnimationFrameId: Int? = null
    private var model = init
    private var previousModel = init
    private var running = true

    private val _updateView: (Double) -> Unit = { updateView() }
    private val receiver: (S) -> Unit = { msg ->
        if (running) {
            val (newModel, cmd) = update(msg, model)
            if (model !== newModel) {
                model = newModel
                if (requestAnimationFrameId == null) {
                    requestAnimationFrameId = window.requestAnimationFrame(_updateView)
                }
            }
            if (cmd != null) {
                handleCmd(cmd)
            }
        }
    }
    private var virtualNode = h("div", view(model).toVirtualNode(receiver))
    private val patch = if (onAfterRender != null) Snabbdom.init({ onAfterRender(root) }) else Snabbdom.init(null)

    init {
        patch(root, virtualNode)
    }

    private fun updateView() {
        if (!running) {
            return
        }

        requestAnimationFrameId = null
        if (previousModel !== model) {
            previousModel = model
            virtualNode = patch(virtualNode, h("div", view(model).toVirtualNode(receiver)))
        }
    }

    private fun handleCmd(cmd: Cmd<S>) {
        when (cmd) {
            is Cmd.Closure -> {
                cmd.body().then {
                    receiver(it)
                }
            }
        }
    }

    @JsName("stop")
    fun stop(): M {
        if (running) {
            running = false

            patch(virtualNode, h("div"))
        }
        return model
    }
}

internal fun <M, S> runProgram(root: Element, init: M, update: Update<M, S>, view: View<M, S>, onAfterRender: (Element) -> Unit): Program<M, S> {
    return Program(root, init, update, view, onAfterRender)
}

@Suppress("unused")
fun <M, S> runProgram(root: Element, init: M, update: Update<M, S>, view: View<M, S>): Program<M, S> {
    return Program(root, init, update, view, null)
}

internal fun <M, S> runBeginnerProgram(root: Element, init: M, update: (S, M) -> M, view: (M) -> Html<S>, onAfterRender: (Element) -> Unit): Program<M, S> {
    return Program(root, init, { s, m -> update(s, m) to null }, view, onAfterRender)
}

@Suppress("unused")
fun <M, S> runBeginnerProgram(root: Element, init: M, update: (S, M) -> M, view: (M) -> Html<S>): Program<M, S> {
    return Program(root, init, { s, m -> update(s, m) to null }, view, null)
}

internal fun runBeginnerProgram(root: Element, view: Html<Unit>, onAfterRender: (Element) -> Unit): Program<Unit, Unit> {
    return Program(root, Unit, { _, _ -> Unit to null }, { view }, onAfterRender)
}

@Suppress("unused")
fun runBeginnerProgram(root: Element, view: Html<Unit>): Program<Unit, Unit> {
    return Program(root, Unit, { _, _ -> Unit to null }, { view }, null)
}