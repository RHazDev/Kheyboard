package fr.rhaz.kheyboard

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.media.MediaPlayer
import android.view.KeyEvent
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Button
import com.afollestad.recyclical.datasource.emptyDataSourceTyped
import com.afollestad.recyclical.setup
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import fr.rhaz.kheyboard.utils.*
import kotlinx.android.synthetic.main.keyboard_risibank.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

class Risibank(val kheyboard: Kheyboard) {

    val stickers = emptyDataSourceTyped<String>()

    var view: View = kheyboard.layoutInflater.inflate(R.layout.keyboard_risibank, null)

    val buttons = view.run {
        listOf(nouveauxbtn, populairesbtn, favorisbtn, aleatoiresbtn)
    }

    var selected = view.populairesbtn
        set(value) {
            field = value
            buttons.forEach { updateButton(it) }
            getStickers()
        }

    fun updateButton(button: Button) = view.run {
        if (button != selected) {
            button.background = resources.getDrawable(R.drawable.shape_naked_button)
        } else {
            button.background = resources.getDrawable(R.drawable.shape_button_dark)
        }
    }

    var loading = true
        set(value) {
            field = value
            updateLoading()
        }

    fun updateLoading() = view.run {
        recycler.isRefreshing = loading
    }

    var error = false
        set(value) {
            field = value
            updateErrorText()
        }

    fun updateErrorText() = view.run {
        if (selected == favorisbtn) {
            errorText.visibility = GONE
            return
        }
        errorText.visibility = when (error) {
            true -> VISIBLE
            false -> GONE
        }
    }

    fun updateFavorisText() = view.run {
        if (selected != favorisbtn) {
            favorisText.visibility = GONE
            return
        }
        favorisText.visibility = when (stickers.isEmpty()) {
            true -> VISIBLE
            false -> GONE
        }
    }

    var data = JSONObject()
        get() = field
        set(value) {
            field = value
            getStickers()
        }

    fun request() = deferred<JSONObject> { resolve, reject ->
        val req = JsonObjectRequest("https://risibank.fr/api/v0/load", null, resolve, reject)
        Volley.newRequestQueue(kheyboard).add(req)
    }

    suspend fun reload() {
        try {
            val res = request().await()
            loading = false
            error = false
            data = res.getJSONObject("stickers")
        } catch (ex: Exception) {
            ex.printStackTrace()
            loading = false
            error = true
            delay(1000)
            reload()
        }
    }

    fun getStickers() = view.run {
        val array = when (selected) {
            nouveauxbtn -> data.array("tms")
            populairesbtn -> data.array("views")
            favorisbtn -> Config(kheyboard).config.array("favoris")
            aleatoiresbtn -> data.array("random")
            else -> return
        }
        stickers.set(kheyboard.getStickers(array))
        recyclerInner.smoothScrollToPosition(0)
        updateFavorisText()
    }

    fun initStickers() = view.run {
        kheyboard.run {
            recyclerInner.setup {
                withDataSource(stickers)
                withStickers(R.layout.keyboard_sticker) {
                    onLongClick {
                        when (selected == favorisbtn) {
                            true -> unfavorite(item)
                            false -> favorite(item)
                        }
                        getStickers()
                        vibrate()
                    }
                }
            }
            recycler.setOnRefreshListener {
                GlobalScope.launch(Dispatchers.Main) {
                    reload()
                }
            }
        }
    }

    fun initButtons() = view.run {
        buttons.forEach { button ->
            button.setOnClickListener {
                selected = button
                kheyboard.vibrate()
            }
        }
    }

    fun initAddButton() = view.run {
        addbtn.setOnClickListener {
            val clipboard = kheyboard.clipboardManager.primaryClip?.getItemAt(0)
            val url = clipboard?.text?.toString()

            if (url == null) {
                kheyboard.toast("Copiez un lien pour l'ajouter aux favoris")
            } else {
                kheyboard.favorite(url)
            }
        }
    }

    fun initDonateButton() = view.run {
        donatebtn.setOnClickListener {
            val intent = Intent(kheyboard, Billing::class.java)
            intent.flags += FLAG_ACTIVITY_NEW_TASK
            kheyboard.startActivity(intent)
            kheyboard.vibrate()
        }
    }

    fun initSearchButton() = view.run {
        searchbtn.setOnClickListener {
            kheyboard.run {
                azerty.switch()
                vibrate()
            }
        }
    }

    fun initLogo() = view.run {
        logo.setOnClickListener {
            kheyboard.inputMethodManager.showInputMethodPicker()
            kheyboard.vibrate()
        }

        logo.setOnLongClickListener {
            MediaPlayer.create(kheyboard, R.raw.risitas).start()
            kheyboard.vibrate()
            true
        }
    }

    fun initBackspaceButton() = view.run {
        backspacebtn.setOnClickListener {
            val input = kheyboard.currentInputConnection
            val selectedText = input.getSelectedText(0)
            if (selectedText == null || selectedText.isEmpty())
                input.deleteSurroundingText(1, 0)
            else input.commitText("", 1)
            kheyboard.vibrate()
        }
    }

    fun initSpaceButton() = view.run {
        spacebtn.setOnClickListener {
            val input = kheyboard.currentInputConnection
            input.commitText(" ", 1)
            kheyboard.vibrate()
        }
    }

    fun initReturnButton() = view.run {
        returnbtn.setOnClickListener {
            kheyboard.run {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                vibrate()
            }
        }
    }

    init {
        initStickers()

        initButtons()

        initAddButton()
        initDonateButton()
        initSearchButton()

        initLogo()

        initBackspaceButton()
        initSpaceButton()
        initReturnButton()

        updateLoading()
        updateButton(selected)

        GlobalScope.launch(Dispatchers.Main) {
            reload()
        }
    }

    fun switch() {
        kheyboard.setInputView(view)
    }
}