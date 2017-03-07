package com.miniccmusicplayer.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.design.widget.NavigationView;
import android.support.design.widget.TabLayout;
import android.support.v4.app.*;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.hersch.musicplayer.R;
import com.miniccmusicplayer.adapter.PagerAdapter;
import com.miniccmusicplayer.bean.MyUser;
import com.miniccmusicplayer.view.CircleImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import cn.bmob.v3.BmobUser;
import cn.bmob.v3.datatype.BmobFile;
import cn.bmob.v3.listener.DownloadFileListener;
import cn.bmob.v3.listener.UpdateListener;
import cn.bmob.v3.listener.UploadFileListener;

public class MainActivity extends AppCompatActivity {
    private Toolbar toolbar;
    private NavigationView navigationView;
    private DrawerLayout drawerLayout;
    private TabLayout tabLayout;
    private ViewPager viewPager;
    private TextView userText;
    private Button loginBtn;
    private List<String> tabList;
    private List<Fragment> mFragmentList;
    private CircleImageView circleImageView;
    private Intent serviceIntent;
    private static final int MUSIC = 0;
    private static final int SEARCH = 1;
    private final int REQUEST_IMAGE_GET = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
        setDrawerLayout();
        initTabPagerList();
        serviceIntent = new Intent(MainActivity.this, MusicService.class);
        startService(serviceIntent);
        Log.i("MainUi", "OnCreate");
    }

    void initViews()//初始化组件
    {
        toolbar = (Toolbar) findViewById(R.id.main_toolbar);
        navigationView = (NavigationView) findViewById(R.id.main_na_view);
        drawerLayout = (DrawerLayout) findViewById(R.id.main_drawer);
        viewPager = (ViewPager) findViewById(R.id.main_viewpager);
        tabLayout = (TabLayout) findViewById(R.id.main_tablayout);
        userText = (TextView) findViewById(R.id.na_view_user_text);
        loginBtn = (Button) findViewById(R.id.na_view_login_btn);
        circleImageView = (CircleImageView) findViewById(R.id.navigationview_head_imageview);
        circleImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MyUser myUser = BmobUser.getCurrentUser(getApplicationContext(), MyUser.class);
                if (myUser != null) {
                    Intent intent = new Intent(Intent.ACTION_PICK,
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    startActivityForResult(intent, REQUEST_IMAGE_GET);
                } else {
                    Toast.makeText(getApplicationContext(), "骚年请先登录", Toast.LENGTH_SHORT).show();
                }
            }
        });
        loginBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                startActivity(intent);
            }
        });
        setUserInfo();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }
    /**
     * 根据本地缓存初始化用户名字
     */
    public void setUserInfo() {
        MyUser myUser = BmobUser.getCurrentUser(getApplicationContext(), MyUser.class);
        if (myUser != null) {
            userText.setText(myUser.getUsername());
            loginBtn.setVisibility(View.GONE);
            BmobFile bmobFile = myUser.getIcon();
            if (bmobFile != null) {
                bmobFile.download(getApplicationContext(), new DownloadFileListener() {
                    @Override
                    public void onSuccess(String s) {
                        circleImageView.setImageBitmap(BitmapFactory.decodeFile(s));
                    }
                    @Override
                    public void onFailure(int i, String s) {
                        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        } else {
            userText.setText("路人");
            loginBtn.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {        //此处的 RESULT_OK 是系统自定义得一个常量
            Log.e("MainAcitivty", "ActivityResult resultCode error");
            return;
        }
        String path = "";
        //此处的用于判断接收的Activity是不是你想要的那个
        if (requestCode == REQUEST_IMAGE_GET) {
            Uri originalUri = data.getData();        //获得图片的uri
            if (originalUri != null) {
                String[] proj = {MediaStore.Images.Media.DATA};
                Cursor cursor = getApplicationContext().getContentResolver().query(originalUri, proj, null, null, null);
                //将光标移至开头 ，这个很重要，不小心很容易引起越界
                cursor.moveToFirst();
                //按我个人理解 这个是获得用户选择的图片的索引值
                int column_index = cursor.getColumnIndex(proj[0]);
                //最后根据索引值获取图片路径
                path = cursor.getString(column_index);
            }
            circleImageView.setImageBitmap(BitmapFactory.decodeFile(path));
            //上传用户图片并更新
            uploadUserIcon(path);
        }
    }

    /**
     * 上传用户头像文件
     */
    public void uploadUserIcon(String picturePath) {
        final BmobFile icon = new BmobFile(new File(picturePath));
        icon.upload(this, new UploadFileListener() {
            @Override
            public void onSuccess() {
                MyUser myUser = new MyUser();
                MyUser currentUser = BmobUser.getCurrentUser(getApplicationContext(), MyUser.class);
                myUser.setIcon(icon);
                //更新表中icon
                myUser.update(getApplicationContext(), currentUser.getObjectId(), new UpdateListener() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(getApplicationContext(), "设置头像成功", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(int i, String s) {
                        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onFailure(int i, String s) {
                Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void setDrawerLayout()//设置抽屉布局
    {
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowHomeEnabled(true);//设置左上角可见图标
        ActionBarDrawerToggle mDrawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar,
                R.string.drawer_open, R.string.drawer_close);//设置开关
        mDrawerToggle.syncState();//初始化状态
        drawerLayout.setDrawerListener(mDrawerToggle);
        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case R.id.drawer_own_music:
                        Toast.makeText(getApplicationContext(), "Music", Toast.LENGTH_SHORT).show();
                        drawerLayout.closeDrawer(Gravity.LEFT);
                        viewPager.setCurrentItem(MUSIC);
                        break;
                    case R.id.drawe_favor_list:
                        Toast.makeText(getApplicationContext(), "List is clicked", Toast.LENGTH_SHORT).show();
                        break;
                    case R.id.drawer_home:
                        Toast.makeText(getApplicationContext(), "Home is clicked", Toast.LENGTH_SHORT).show();
                        break;
                    case R.id.drawer_share:
                        Toast.makeText(getApplicationContext(), "Share is clicked", Toast.LENGTH_SHORT).show();
                        break;
                    case R.id.drawer_surround:
                        Toast.makeText(getApplicationContext(), "Surround is clicked", Toast.LENGTH_SHORT).show();
                        break;
                    case R.id.drawer_exit:
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        builder.setMessage("退出当前账号");
                        builder.setTitle("葱头小提示");
                        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                BmobUser.logOut(getApplicationContext());
                                LoginActivity.ENTER_MAIN = false;
                                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                                startActivity(intent);
                            }
                        });
                        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                        builder.show();
                }
                return true;
            }
        });
    }

    /**
     * 设置页卡布局
     */
    void initTabPagerList() {
        //inti tabList
        tabList = new ArrayList<String>();
        tabList.add("Music");
        tabList.add("Search");

        //init viewLists
        mFragmentList = new ArrayList<>();
        mFragmentList.add(new MainMusicFragment());
        mFragmentList.add(new MainSearchFragment());
        //set the Mode
        tabLayout.setTabMode(TabLayout.MODE_FIXED);
        //add Tab into TabLayout
        tabLayout.addTab(tabLayout.newTab().setText(tabList.get(0)));
        tabLayout.addTab(tabLayout.newTab().setText(tabList.get(1)));
        //设置标签点击响应
        tabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });
        PagerAdapter mPagerAdapter = new PagerAdapter(getSupportFragmentManager(), mFragmentList, tabList);
        viewPager.setAdapter(mPagerAdapter);
        tabLayout.setupWithViewPager(viewPager);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode==KeyEvent.KEYCODE_BACK){
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            startActivity(intent);
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_toolbar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        switch (item.getItemId()) {
            case R.id.main_toolbar_setting_sign:
                intent = new Intent(MainActivity.this, RegisterActivity.class);
                startActivity(intent);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i("MainActivity","OnDestroy");
        SharedPreferences sharedPreferences = getSharedPreferences("enterCount", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.commit();
    }
}
