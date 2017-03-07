package com.miniccmusicplayer.ui;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.hersch.musicplayer.R;
import com.miniccmusicplayer.bean.MyUser;

import cn.bmob.v3.BmobUser;
import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.LogInListener;
import cn.bmob.v3.listener.SaveListener;

public class LoginActivity extends AppCompatActivity {
    private Button registerBtn;
    private Button loginBtn;
    private TextView otherLoginText;
    private EditText userEdit;
    private EditText pwdEdit;
    public static boolean ENTER_MAIN = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        userEdit = (EditText) findViewById(R.id.login_user_edit);
        pwdEdit = (EditText) findViewById(R.id.login_pwd_edit);
        registerBtn = (Button) findViewById(R.id.login_register_btn);
        loginBtn = (Button) findViewById(R.id.login_login_btn);
        otherLoginText = (TextView) findViewById(R.id.login_other_login_text);
        registerBtn.setOnClickListener(listener);
        loginBtn.setOnClickListener(listener);
        otherLoginText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }

    View.OnClickListener listener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.login_register_btn:
                    Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
                    startActivity(intent);
                    break;
                case R.id.login_login_btn:
                    //进行用户名以及密码的判断
                    LoginCurrentUser();
                    break;
            }
        }
    };

    /**
     * 检测登录操作，检测用户名和手机
     */
    public void LoginCurrentUser() {
        //方法默认支持手机和用户名两种方法登录
        BmobUser bmobUser = new BmobUser();
        String username = userEdit.getText().toString();
        String pwd = pwdEdit.getText().toString();
        bmobUser.setUsername(username);
        bmobUser.setPassword(pwd);
        bmobUser.loginByAccount(getApplicationContext(), username, pwd, new LogInListener<MyUser>() {
            @Override
            public void done(MyUser myUser, BmobException e) {
                  if(myUser!=null){
                      Toast.makeText(getApplicationContext(), "登录成功", Toast.LENGTH_SHORT).show();
                      jumpToMainAty();
                  }
                  else{
                      Toast.makeText(getApplicationContext(), "用户名或密码错误", Toast.LENGTH_SHORT).show();
                  }
            }
        });
    }

    /**
     * 登录界面跳转到主界面
     */
    public void jumpToMainAty() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                this.finish();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i("LoginActivity", "OnDestory");
    }
}
