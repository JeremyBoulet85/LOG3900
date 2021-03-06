package com.example.client_leger.Fragments

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.example.client_leger.Adapters.LobbyCardsRecyclerViewAdapter
import com.example.client_leger.Communication.Communication
import com.example.client_leger.ConnexionController
import com.example.client_leger.Controller.LobbyCardsController
import com.example.client_leger.Interface.FragmentChangeListener
import com.example.client_leger.R
import com.example.client_leger.models.GameMode
import com.example.client_leger.models.Lobby
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.android.synthetic.main.dialog_createlobby.*
import kotlinx.android.synthetic.main.fragment_gamecards.view.*
import kotlinx.android.synthetic.main.lobbycard_layout.*
import org.json.JSONObject


class LobbyCardsFragment : Fragment(), LobbyCardsRecyclerViewAdapter.ItemClickListener,
    FragmentChangeListener {
    lateinit var username: String
    lateinit var userListAdapter: ArrayAdapter<String>
    private lateinit var adapterLobbyCards: LobbyCardsRecyclerViewAdapter
    private lateinit var recyclerViewGameCards: RecyclerView
    private lateinit var lobbyCardsController: LobbyCardsController
    private lateinit var connexionController: ConnexionController
    private lateinit var lobbyCards: ArrayList<Lobby>
    private lateinit var userList: ArrayList<String>
    private lateinit var spinnerGameModes: Spinner
    private lateinit var lobbyNotifSub: Disposable
    private lateinit var inviteSub: Disposable
    private lateinit var v: View
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        v = inflater.inflate(R.layout.fragment_gamecards, container, false)
        username = activity!!.intent.getStringExtra("username")
        lobbyCardsController = LobbyCardsController()
        connexionController = ConnexionController()
        lobbyCards = ArrayList()
        userList = ArrayList()
        userListAdapter = ArrayAdapter<String>(
            context!!,
            android.R.layout.simple_list_item_1,
            userList
        )

        recyclerViewGameCards = v.findViewById(R.id.recyclerView_gameCards)
        val numberOfColumns = 2
        recyclerViewGameCards.layoutManager = GridLayoutManager(context, numberOfColumns)
        adapterLobbyCards =
            LobbyCardsRecyclerViewAdapter(
                context,
                lobbyCards
            )
        adapterLobbyCards.setClickListener(this)
        recyclerViewGameCards.adapter = adapterLobbyCards

        val buttonShowDialog: Button = v.findViewById(R.id.button_showCreateLobbyDialog)
        buttonShowDialog.setOnClickListener {
            buttonShowDialog.isEnabled = false
            showDialog()
        }

        spinnerGameModes = v.findViewById(R.id.GameMode)
        val gamemodes = arrayListOf("Free for all", "Sprint Solo", "Sprint Co-op")
        val dataAdapter = ArrayAdapter(context!!, R.layout.gamemode_item, gamemodes)
        spinnerGameModes.adapter = dataAdapter
        spinnerGameModes.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {

            }

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                v.textView_Description.text= getDescription(spinnerToGameMode(position))
                lobbyCardsController.getLobbies(
                    this@LobbyCardsFragment,
                    spinnerToGameMode(position).toString()
                )
            }

        }

        lobbyNotifSub = Communication.getLobbyUpdateListener().subscribe { mes ->
            when (mes.getString("type")) {
                "create" -> {
                    val user = mes.getJSONArray("usernames").getString(0)

                    if (username == user) {
                        val fragment = LobbyFragment()
                        val bundle = Bundle()
                        bundle.putBoolean("isMaster", true)
                        bundle.putSerializable("lobby", context?.let { Lobby(mes, it)})
                        fragment.arguments = bundle

                        replaceFragment(fragment)
                    } else {
                        if(mes.getString("mode") == getCurrentGameMode().toString()){
                                activity!!.runOnUiThread {
                                    adapterLobbyCards.addItem(context?.let { Lobby(mes, it) })
                                }
                            }
                    }
                }
                "join" -> {
                    val user = mes.getString("username")

                    if (username == user) {
                        val fragment = LobbyFragment()
                        val bundle = Bundle()
                        bundle.putBoolean("isMaster", false)
                        bundle.putSerializable("lobby", context?.let { Lobby(mes, it)})
                        fragment.arguments = bundle


                        replaceFragment(fragment)

                    } else {
                        if (mes.getString("mode") == getCurrentGameMode().toString()) {
                            activity!!.runOnUiThread {
                                adapterLobbyCards.updateUserJoin(mes.getString("lobbyName"), user)
                            }
                        }
                    }
                }
                "leave"->{
                    val user = mes.getString("username")
                    if (username != user) {
                        if (mes.getString("mode") == getCurrentGameMode().toString()) {
                            activity!!.runOnUiThread {
                                adapterLobbyCards.updateUserLeave(mes.getString("lobbyName"), user)
                            }
                        }
                    }
                }
                "delete" -> {
                    if (mes.getString("mode") == getCurrentGameMode().toString()) {
                        activity!!.runOnUiThread {
                            adapterLobbyCards.removeItem(context?.let { Lobby(mes, it) })
                        }
                    }

                }
            }
        }

        inviteSub = Communication.getInvitationUpdateListener().subscribe{mes ->
            if (mes["type"] == "invitation") {
                val builder = AlertDialog.Builder(context)
                builder.setTitle("Notification")
                builder.setMessage("You have been invited to lobby: " + mes["lobbyName"].toString());
                builder.setPositiveButton("Accept") { dialog, _ ->
                    val data = JSONObject()
                    data.put("username", username)
                    data.put("lobbyName", mes["lobbyName"].toString())
                    data.put("isPrivate", true)
                    data.put("password", "invited")
                    lobbyCardsController.joinLobby(this, data)
                }
                builder.setNeutralButton("Cancel") { dialog, _ ->
                    dialog.cancel()
                }
                activity!!.runOnUiThread {
                    val mDialog = builder.create()
                    mDialog.show()
                }
            }
        }

        return v
    }

    override fun onDestroy() {
        super.onDestroy()
        lobbyNotifSub.dispose()
        inviteSub.dispose()
    }

    private fun getDescription(mode: GameMode): String {
        return when (mode) {
            GameMode.FFA -> "Play against one or more players and try to accumulate the most points"
            GameMode.SOLO -> "Play alone and try to guess the most words"
            GameMode.COOP -> "Play with up to three players and try to guess the most words together"
        }
    }

    private fun showDialog() {
        val d = Dialog(context!!)
        d.setTitle("NumberPicker")
        d.setContentView(R.layout.dialog_createlobby)
        val playerLimit: TextView =  d.findViewById(R.id.textView_PlayerLimit)
        val np: NumberPicker = d.findViewById(R.id.np__numberpicker_input)
        val switch: Switch = d.findViewById(R.id.switch_private)
        val button: Button = d.findViewById(R.id.button_CreateLobby)
        if(getCurrentGameMode() == GameMode.SOLO){
            playerLimit.visibility = View.GONE
            np.visibility = View.GONE
            switch.visibility = View.GONE
            button.setOnClickListener {
                val data = JSONObject()
                data.put("username", username)
                data.put("lobbyName", d.findViewById<EditText>(R.id.lobbyname).text.trim())
                data.put("isPrivate", true)
                data.put("mode", GameMode.SOLO)
                data.put("size", 1)
                data.put("password", "solo")
                lobbyCardsController.joinLobby(this, data)
                d.dismiss()
            }
        } else {
            if (getCurrentGameMode() == GameMode.FFA) {
                np.maxValue = 9
                np.minValue = 2
            } else if (getCurrentGameMode() == GameMode.COOP) {
                np.maxValue = 4
                np.minValue = 1
            }

            np.wrapSelectorWheel = true

            switch.setOnCheckedChangeListener { _, isChecked ->
                d.findViewById<EditText>(R.id.lobbyPassword).visibility =
                    if (isChecked) View.VISIBLE else View.GONE
            }

            button.setOnClickListener {
                if (validateLobbyFields(d)) {
                    val data = JSONObject()
                    data.put("username", username)
                    data.put("isPrivate", d.findViewById<Switch>(R.id.switch_private).isChecked)
                    data.put("lobbyName", d.findViewById<EditText>(R.id.lobbyname).text.trim())
                    data.put(
                        "size",
                        d.findViewById<NumberPicker>(R.id.np__numberpicker_input).value
                    )
                    if (switch.isChecked) data.put(
                        "password",
                        d.findViewById<EditText>(R.id.lobbyPassword).text.trim()
                    )
                    data.put(
                        "mode",
                        spinnerToGameMode(spinnerGameModes.selectedItemPosition).toString()
                    )
                    lobbyCardsController.joinLobby(this, data)
                    d.dismiss()
                    closeKeyboard()
                }
            }
        }
        d.setOnDismissListener {
            closeKeyboard()
            v.findViewById<Button>(R.id.button_showCreateLobbyDialog).isEnabled = true }
        d.show()
    }

    private fun closeKeyboard() {
        if ( activity!!.currentFocus != null){
            val imm: InputMethodManager = activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(activity!!.currentFocus!!.windowToken, 0)
        }
    }

    private fun getCurrentGameMode(): GameMode{
        return spinnerToGameMode(spinnerGameModes.selectedItemPosition)
    }

    override fun onItemClick(view: View?, position: Int) { }

    override fun onJoinPrivateClick(view: View?, adapterPosition: Int) {
        view!!.findViewById<Button>(R.id.button_joinPrivate).isEnabled = false
        val lobby = adapterLobbyCards.getItem(adapterPosition)
        if(lobby.size > lobby.usernames.size) {
            val builder = AlertDialog.Builder(context)
            builder.setTitle("Enter password")
            val input = EditText(context)
            input.inputType = InputType.TYPE_TEXT_VARIATION_PASSWORD
            builder.setView(input)

            builder.setPositiveButton(
                "JOIN"
            ) { dialog, _ ->

                val data = JSONObject()
                data.put("username", username)
                data.put("isPrivate", true)
                data.put("lobbyName", lobby.lobbyName)
                data.put("password", input.text.trim())

                lobbyCardsController.joinLobby(this, data)
                dialog.dismiss()
            }

            builder.setNeutralButton(
                "Cancel"
            ) { dialog, _ -> dialog.cancel() }

            builder.show()
        } else{
            Toast.makeText(context, " The lobby is full", Toast.LENGTH_SHORT).show()
            view!!.findViewById<Button>(R.id.button_joinPrivate).isEnabled = true

        }
        view!!.findViewById<Button>(R.id.button_joinPrivate).isEnabled = true


    }

    override fun onJoinClick(view: View?, position: Int) {
        view!!.findViewById<Button>(R.id.button_join).isEnabled = false
        val lobby = adapterLobbyCards.getItem(position)
        if(lobby.size > lobby.usernames.size) {
            val data = JSONObject()
            data.put("username", username)
            data.put("lobbyName", lobby.lobbyName)
            lobbyCardsController.joinLobby(this, data)
        } else{
            view!!.findViewById<Button>(R.id.button_join).isEnabled = true
            Toast.makeText(context, " The lobby is full", Toast.LENGTH_SHORT).show()
        }

    }

    override fun onUsersDropClick(view: View?, position: Int) {
        if (view != null) {
            toggleView(view)
        }
    }

    override fun replaceFragment(fragment: Fragment) {
        fragmentManager!!.beginTransaction().replace(R.id.container_view_right, fragment).commit()
    }

    fun loadLobbies(lobbies: ArrayList<Lobby>) {
        adapterLobbyCards.setItems(lobbies)
    }

    private fun toggleView(v: View) {
        v.visibility = if (v.isShown) View.GONE else View.VISIBLE
    }

    private fun validateLobbyFields(d: Dialog): Boolean {
        return when {
            d.lobbyname.text.isBlank() -> {
                d.lobbyname.error = "Enter a Lobby Name"
                d.lobbyname.requestFocus()
                false
            }
            d.lobbyname.text.length > 20 -> {
                d.lobbyname.error = "Lobby name must be between 1 and 20 characters"
                d.lobbyname.requestFocus()
                false
            }
            d.switch_private.isChecked -> {
                when {
                    d.lobbyPassword.text.isBlank() -> {
                        d.lobbyPassword.error = "Enter a password"
                        d.lobbyPassword.requestFocus()
                        false
                    }
                    d.lobbyPassword.text.length > 20 -> {
                        d.lobbyPassword.error = "Lobby password must be between 1 and 20 characters"
                        d.lobbyPassword.requestFocus()
                        false
                    }
                    else -> true
                }
            }
            else -> true
        }

    }

    private fun spinnerToGameMode(id: Int): GameMode {
        return when (id) {
            0 -> GameMode.FFA
            1 -> GameMode.SOLO
            2 -> GameMode.COOP
            else -> GameMode.FFA
        }
    }
}