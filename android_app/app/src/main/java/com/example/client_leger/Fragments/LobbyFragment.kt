package com.example.client_leger.Fragments

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.example.client_leger.Communication.Communication
import com.example.client_leger.Controller.GameController
import com.example.client_leger.Interface.FragmentChangeListener
import com.example.client_leger.R
import io.reactivex.rxjava3.disposables.Disposable
import org.json.JSONArray
import org.json.JSONObject


class LobbyFragment : Fragment(),
    FragmentChangeListener {
    private var gameController: GameController = GameController()
    private lateinit var username: String
    private lateinit var lobbyName:String
    private var usernames: ArrayList<String> = arrayListOf()

    lateinit var startListener: Disposable;

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_lobby, container, false)
        username = activity!!.intent.getStringExtra("username")
        val bundle = this.arguments
        lobbyName = ""
        if (bundle != null) {
            lobbyName = bundle.getString("lobbyName")
        }
        gameController.getUsers(this, lobbyName)

        startListener = Communication.getGameStartListener().subscribe{res ->
            activity!!.runOnUiThread {
                replaceFragment(GameplayFragment())
            }
        }
        return  v
    }

    override fun onDestroy() {
        super.onDestroy()
        startListener.dispose()
    }

    private fun startGame(lobbyName: String){
        gameController.startGame(this, lobbyName)
    }
    private fun leaveGame(lobbyName: String){
        var body = JSONObject()
        body.put("lobbyName", lobbyName)
        body.put("username", username)
        gameController.leaveGame(this, body)
    }
    override fun replaceFragment(fragment: Fragment) {
        fragmentManager!!.beginTransaction().replace(R.id.container_view_right, fragment)
            .addToBackStack(fragment.toString()).commit()
    }

    fun loadUsers(userJsonArray: JSONArray) {
        for (i in 0 until userJsonArray.length()){
            usernames.add(userJsonArray.get(i).toString())
        }
        if(usernames.isNotEmpty()) {
            var startButton = view!!.findViewById<Button>(R.id.button_start)
            var leaveButton = view!!.findViewById<Button>(R.id.button_leave)
            if(usernames[0] == username) {
                startButton.visibility = View.VISIBLE
                startButton.isEnabled = true
                startButton.setOnClickListener { startGame(lobbyName) }
            }else{
                leaveButton.visibility = View.VISIBLE
                leaveButton.isEnabled = true
                leaveButton.setOnClickListener { leaveGame(lobbyName) }
            }
        }
    }
}