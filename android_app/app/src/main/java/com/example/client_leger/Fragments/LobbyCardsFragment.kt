package com.example.client_leger.Fragments

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_gamecards, container, false)
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
        buttonShowDialog.isEnabled = false
        buttonShowDialog.setOnClickListener { showDialog() }

        spinnerGameModes = v.findViewById(R.id.GameMode)
        val gamemodes = arrayListOf("Select Game Mode", "Free for all", "Sprint Solo", "Sprint Co-op")
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
                if (position > 0)
                    buttonShowDialog.isEnabled = true
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
                    //First user should always be the lobby creator.

                    if (username == user) {
                        val fragment = LobbyFragment()
                        val bundle = Bundle()
                        bundle.putString("lobbyName", mes.getString("lobbyName"))
                        bundle.putString("mode", mes.getString("mode"))
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
                        bundle.putString("lobbyName", mes.getString("lobbyName"))
                        fragment.arguments = bundle
                        replaceFragment(fragment)
                    } else {
                        if (mes.getString("mode") == getCurrentGameMode().toString()) {
                            activity!!.runOnUiThread {
                                adapterLobbyCards.updateUser(mes.getString("lobbyName"), user)
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

        return v
    }

    override fun onDestroy() {
        super.onDestroy()
        lobbyNotifSub.dispose()
    }

    private fun showDialog() {
        val d = Dialog(context!!)
        d.setTitle("NumberPicker")
        d.setContentView(R.layout.dialog_createlobby)
        val np: NumberPicker = d.findViewById(R.id.np__numberpicker_input)
        np.maxValue = 9
        np.minValue = 2
        np.wrapSelectorWheel = true

        val switch: Switch = d.findViewById(R.id.switch_private)
        switch.setOnCheckedChangeListener { _, isChecked ->
            d.findViewById<EditText>(R.id.lobbyPassword).visibility =
                if (isChecked) View.VISIBLE else View.GONE
        }


        val button: Button = d.findViewById(R.id.button_CreateLobby)
        button.setOnClickListener {
            if (validateLobbyFields(d)) {
                val data = JSONObject()
                data.put("username", username)
                data.put("isPrivate", d.findViewById<Switch>(R.id.switch_private).isChecked)
                data.put("lobbyName", d.findViewById<EditText>(R.id.lobbyname).text.trim())
                data.put("size", d.findViewById<NumberPicker>(R.id.np__numberpicker_input).value)
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
            }
        }
        d.show()
    }

    private fun getCurrentGameMode(): GameMode{
        return spinnerToGameMode(spinnerGameModes.selectedItemPosition)
    }

    override fun onItemClick(view: View?, position: Int) {


    }

    override fun onJoinPrivateClick(view: View?, adapterPosition: Int) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Enter password")
        val input = EditText(context)
        input.inputType = InputType.TYPE_TEXT_VARIATION_PASSWORD
        builder.setView(input)

        builder.setPositiveButton(
            "JOIN"
        ) { dialog, _ ->

            val lobby = JSONObject()
            lobby.put("username", username)
            lobby.put("isPrivate", true)
            lobby.put("lobbyName", adapterLobbyCards.getItem(adapterPosition).lobbyName)
            lobby.put("password", input.text.trim())

            lobbyCardsController.joinLobby(this, lobby)
            dialog.dismiss()
        }

        builder.setNeutralButton(
            "Cancel"
        ) { dialog, _ -> dialog.cancel() }

        builder.show()

    }

    override fun onJoinClick(view: View?, position: Int) {
        val lobby = JSONObject()
        lobby.put("username", username)
        lobby.put("lobbyName", adapterLobbyCards.getItem(position).lobbyName)
        lobbyCardsController.joinLobby(this, lobby)
    }

    override fun onUsersDropClick(view: View?, position: Int) {
        if (view != null) {
            toggleView(view)
        }
    }

    override fun replaceFragment(fragment: Fragment) {
        fragmentManager!!.beginTransaction().replace(R.id.container_view_right, fragment)
            .addToBackStack(fragment.toString()).commit()
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
            1 -> GameMode.FFA
            2 -> GameMode.SOLO
            3 -> GameMode.COOP
            else -> GameMode.FFA
        }
    }
}