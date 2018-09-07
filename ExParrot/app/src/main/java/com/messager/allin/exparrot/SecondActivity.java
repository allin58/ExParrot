package com.messager.allin.exparrot;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

//import com.example.a1.myapplication.messagerAPI.LayerImpl;

import com.messager.allin.exparrot.messagerAPI.LayerImpl;

import java.util.ArrayList;
import java.util.Map;

public class SecondActivity extends AppCompatActivity {
    public LayerImpl LI;
    ArrayList<String> names = new ArrayList<String>();
    ListView listView;
    ArrayAdapter<String> adapter;

    ArrayList<String> messages = new ArrayList<String>();
    ListView listView2;
    ArrayAdapter<String> adapter2;

    String selectedItem;

    EditText editText;
    TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        editText=(EditText)findViewById(R.id.editText);
        textView = (TextView) findViewById(R.id.editText);


       // final String username = getIntent().getExtras().getString("username");
        //final String server = getIntent().getExtras().getString("server");
        listView = (ListView) findViewById(R.id.list_of_users);
        adapter= new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, names);
        listView.setAdapter(adapter);

        listView2 = (ListView) findViewById(R.id.list_of_messages);
        adapter2= new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, messages);
        listView2.setAdapter(adapter2);

        try {
            LI = LayerImpl.getInst();
        } catch (Exception e) {
            e.printStackTrace();
        }


        Thread thread = new Thread(new Runnable() {

            public void run() {

                Map map;



                while (true){


                    try {
                        Thread.sleep(2000);////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
                        synchronized (LI) {

                            synchronized (names) {
                                map = LI.check();
                                names.clear();
                                for (Object o : LI.getAllUsers()) {
                                    names.add((String)o);


                                }
                            }

                        }



                        if (map.keySet().size() > 0) {
                            String s =  map.get("sender")+" : "+  map.get("msg");

                            messages.add(s);


                            map.clear();
                        }

                        ////////////////////////////////////////////////////////////////////////  так можно достучаться до ui потока
                        runOnUiThread(new Runnable() {
                            public void run() {

                                adapter.notifyDataSetChanged();          ////// обновляем список пользователей
                                adapter2.notifyDataSetChanged();          ////// обновляем список сообщений



                            }
                        });

                                 ////////////////////////////////////////////////////////////////////



                    } catch (Exception e) {
                        //  Toast.makeText(MainActivity.this,"something goes wrong",Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
        thread.start();






        listView.setOnItemClickListener(new AdapterView.OnItemClickListener(){
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id)
            {
                // по позиции получаем выбранный элемент

                synchronized (names) {
                    selectedItem = names.get(position);
                }
              }
        });





    }


public void buttonSend(View v)throws Exception{


    Thread thread = new Thread(new Runnable() {

        public void run() {

            try {


             
                    LI.sendMSG(selectedItem,textView.getText().toString());
                    messages.add("to "+selectedItem+" : " +textView.getText().toString());
                    editText.getText().clear();





            } catch (Exception e) {
                e.printStackTrace();
            }


        }
    });
    thread.start();



   // editText.setText("");

}















}
