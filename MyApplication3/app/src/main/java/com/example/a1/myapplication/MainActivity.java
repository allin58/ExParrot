package com.example.a1.myapplication;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.content.Intent;
import android.widget.Toast;

import com.example.a1.myapplication.messagerAPI.LayerImpl;

import java.security.Security;
import java.util.Map;
import java.util.concurrent.Exchanger;


public class MainActivity extends AppCompatActivity {

private Button btn;




public LayerImpl LI;


    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        addListenerOnButton();

        EditText editText1=(EditText)findViewById(R.id.editText3);
        EditText editText2=(EditText)findViewById(R.id.editText6);

        editText1.setText("Marko");
        editText2.setText("http://192.168.0.101:8080");


    }


    public void addListenerOnButton(){



        btn=(Button)findViewById(R.id.button2);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {


              /*  TextView textView1 = (TextView) findViewById(R.id.editText3);
                TextView textView2 = (TextView) findViewById(R.id.editText6);


                String username = textView1.getText().toString();
                String server = textView2.getText().toString();

                Intent intent = new Intent("com.example.a1.myapplication.SecondActivity");
                intent.putExtra("username",username);
                intent.putExtra("server",server);
                startActivity(intent);*/



/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


                try {
                    LI = LayerImpl.getInst();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                Thread thread = new Thread(new Runnable() {

                    public void run() {



                            TextView textView1 = (TextView) findViewById(R.id.editText3);
                            TextView textView2 = (TextView) findViewById(R.id.editText6);


                           final String username = textView1.getText().toString();
                           final String server = textView2.getText().toString();


                        try {



                            LI.setUsername(username);

                            LI.setServer(server);

                            LI.handShakeWithServer();


                            Intent intent = new Intent("com.example.a1.myapplication.SecondActivity");
                            //intent.putExtra("username",username);
                            // intent.putExtra("server",server);
                            startActivity(intent);
                        } catch (Exception e) {



                            runOnUiThread(new Runnable() {
                                public void run() {

                                    Toast.makeText(MainActivity.this,"something goes wrong ",Toast.LENGTH_SHORT).show();
                                }
                            });
                        }



                    }
                });
                thread.start();

            }
        }
        );
    }










}




   /* Thread thread = new Thread(new Runnable() {

        public void run() {


            try {
                TextView textView1 = (TextView) findViewById(R.id.editText3);
                TextView textView2 = (TextView) findViewById(R.id.editText6);


                String username = textView1.getText().toString();
                String server = textView2.getText().toString();


                LI = new LayerImpl(username, server);
                LI.handShakeWithServer();
                LI.check();
                Intent intent = new Intent("com.example.a1.myapplication.SecondActivity");


                startActivity(intent);

            } catch (Exception e) {

                // Toast.makeText(MainActivity.this,"something goes wrong",Toast.LENGTH_SHORT).show();
            }


        }
    });
                thread.start();*/
