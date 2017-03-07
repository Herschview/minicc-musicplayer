package com.miniccmusicplayer.ui;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Message;
import android.os.Handler;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.example.hersch.musicplayer.R;
import com.miniccmusicplayer.bean.Song;
import com.miniccmusicplayer.view.MyLrcTextView;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Hersch on 2016/5/15.
 */
public class LrcContentFragment extends Fragment {
    private MyLrcTextView lrcText;
    private MusicService musicService;
    private LrcContentGetter mLrcContentGetter;
    private Song song;
    private final int MSG_ERROR = 1;
    private final int MSG_SUCCESS = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.lrc_frg_content, container, false);
        findViews(view);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentManager mFragmentManager = getActivity().getSupportFragmentManager();
                FragmentTransaction mFragmentTransaction = mFragmentManager.beginTransaction();
                mFragmentTransaction.replace(R.id.sub_fragment, new LrcAlbumImageFragment());
                mFragmentTransaction.commit();
            }
        });
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        searchLrcThread();
    }

    public void findViews(View view) {
        lrcText = (MyLrcTextView) view.findViewById(R.id.lrc_text);
        lrcText.setLrcContentList(new ArrayList<String>());//初始化歌词数据结构，防止空指针
    }

    public Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SUCCESS:
                    lrcText.setLrcContentList(mLrcContentGetter.getLrcContentList());
                    handler.post(updateLrcRunnable);
                    break;
                case MSG_ERROR:
                    ArrayList<String> strings = new ArrayList<>();
                    strings.add("未找到歌词");
                    lrcText.setLrcContentList(strings);
                    lrcText.invalidate();
                    break;
            }
        }
    };

    /**
     * 获取歌词
     */
    public void searchLrcThread() {
        LrcActivity lrcUi = (LrcActivity) getActivity();
        musicService = lrcUi.getService();
        int index = musicService.getPlayIndex();
        song = musicService.getSongList().get(index);
        //联网
        if (detectNet(lrcUi.getApplicationContext())) {
            mLrcContentGetter = new LrcContentGetter();//获取歌词类
            mLrcContentGetter.start();//开启线程
        } else {
            ArrayList<String> strings = new ArrayList<>();
            strings.add("网络未连接，请先连接");
            lrcText.setLrcContentList(strings);
            lrcText.invalidate();
        }
    }

    Runnable updateLrcRunnable = new Runnable() {
        @Override
        public void run() {
            Log.i("update", "update");
            lrcText.setIndex(currentLrcLine());//设置当前正在播放所在行
            lrcText.invalidate();//更新自定义的textView
            handler.postDelayed(this, 1000);
        }
    };

    /**
     * 根据时间戳计算当前时间应该播放哪一行歌词
     *
     * @return
     */
    public int currentLrcLine() {
        int index = 0;
        int time = musicService.getCurrentPosition();
        List<Integer> timeList = mLrcContentGetter.getTimeList();
        List<String> lrcContentList = mLrcContentGetter.getLrcContentList();
        for (int i = 0; i < lrcContentList.size(); i++) {
            if (time < timeList.get(i) && i == 0) {
                index = 0;
                break;
            } else if (i < timeList.size() - 1 && time >= timeList.get(i) && time < timeList.get(i + 1)) {
                index = i;
                break;
            } else if (time >= timeList.get(i) && i == timeList.size() - 1) {
                index = i;
                break;
            }
        }
        return index;
    }

    /**
     * 检测网络连接状态
     *
     * @param context
     * @return
     */
    public boolean detectNet(Context context) {
        try {
            // 获取连接管理对象
            ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivity != null) {
                // 获取活动的网络连接
                NetworkInfo info = connectivity.getActiveNetworkInfo();
                //存在bug--即连接了wifi不过还未登录用户名和密码的情况，故ping一下
                if (info != null && info.isConnected() && ping()) {
                    if (info.getState() == NetworkInfo.State.CONNECTED) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.i("LrcContentFragment", "Exception");
        }
        return false;
    }

    /**
     * 检测局域网下连接了WIFI但是还没有登录，比如校园网
     *
     * @return
     */
    public boolean ping() {
        int result = 1;
        try {
            URL url = new URL("http://www.baidu.com");
            HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
            result = 0;
        } catch (IOException e) {
            Log.i("ping", "ping is error");
            e.printStackTrace();
        } finally {
            if (result == 1) {
                return false;
            } else {
                return true;
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i("LrcContentFragment", "OnDestroy");
        if (handler != null) {
            handler.removeCallbacks(updateLrcRunnable);
        }
    }

    /**
     * 内部类 获取歌词
     */
    class LrcContentGetter extends Thread {
        private ArrayList<String> lrcContentList;//歌词表
        private ArrayList<Integer> timeList;//歌词时间戳表
        private final int CONTENT_MIN_SIZE = 20;

        @Override
        public void run() {
            lrcContentList = new ArrayList<>();
            timeList = new ArrayList<>();
            lrcContentList.clear();
            timeList.clear();
            getLyric();
        }

        public ArrayList<String> getLrcContentList() {
            return lrcContentList;
        }

        public ArrayList<Integer> getTimeList() {
            return timeList;
        }

        /**
         * @param
         */
        public void getLyric() {
            URL url = null;
            HttpURLConnection httpURLConnection = null;
            try {
                //中文必须经过gbk也就是GB2312的转码，因为api采用的是gbk编码格式
                //http://qqmusic.qq.com/fcgi-bin/qm_getLyricId.fcg?name=连哭都是我的错&singer=东来东往&from=qqplayer
//                url = new URL("http://qqmusic.qq.com/fcgi-bin/qm_getLyricId.fcg?name=" + URLEncoder.encode(song.getTitle(), "gbk")
//                        + "&singer=" + URLEncoder.encode(song.getArtist(), "gbk") + "&from=qqplayer");
                url = new URL("http://s.music.qq.com/fcgi-bin/music_search_new_platform?t=0&n=1000&aggr=1&cr=1&loginUin=0" +
                        "&format=json&inCharset=GB2312&outCharset=utf-8&notice=0&platform=jqminiframe.json&needNewCode=0&p=1&" +
                        "catZhida=0&remoteplace=sizer.newclient.next_song&w="+song.getArtist());
                httpURLConnection = (HttpURLConnection) url.openConnection();
                httpURLConnection.setRequestMethod("GET");
                if (httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    String lyricStr = getXMLFromURL(httpURLConnection);
                    //利用正则表达式找到“f:^[0-9]{1,}$|song.getTitle
                    Pattern p = Pattern.compile("\"f\":\"[0-9]+\\|" + song.getTitle());
                   // Pattern p = Pattern.compile("\"f\":");
                    Matcher m = p.matcher(lyricStr);
                    //int count = countLyric(lyricStr);//获得歌词版本的数量
                    if (!m.find()) {
                        Message msg = new Message();
                        Bundle bundle = new Bundle();
                        bundle.putString("error", "no lyric found");
                        msg.setData(bundle);
                        msg.what = MSG_ERROR;
                        handler.sendMessage(msg);
                        return;
                    } else {
                            int startIndex = m.group().indexOf("\":");
                            int endIndex = m.group().indexOf("|", startIndex);
                            String strId = m.group().substring(startIndex + 3, endIndex);
                            int id = Integer.parseInt(strId);
                            //http://music.qq.com/miniportal/static/lyric/歌曲ID求余100/歌曲ID.xml
                            //获取歌词的xml文件
                            url = new URL("http://music.qq.com/miniportal/static/lyric/" + id % 100 + "/" + id + ".xml");
                            httpURLConnection = (HttpURLConnection) url.openConnection();
                            lrcContentList = getDataFromURL(httpURLConnection);
                            //找到最合适的歌词则发送给歌词界面并跳出
                            if (lrcContentList.size() >= CONTENT_MIN_SIZE) {
                                Message msg = new Message();
                                msg.what = MSG_SUCCESS;
                                handler.sendMessage(msg);
                                return;
                            }
                            timeList.clear();
                            lrcContentList.clear();
                        //未找到歌词返回error
                        Message msg = new Message();
                        msg.what = MSG_ERROR;
                        handler.sendMessage(msg);
                        return;
                    }
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (httpURLConnection != null) {
                    httpURLConnection.disconnect();
                }
            }
        }

        /**
         * 获取歌词xml文件以及其中的歌词id号
         *
         * @param httpURLConnection
         * @return
         */
        public String getXMLFromURL(HttpURLConnection httpURLConnection) {
            String str = "";
            String xmlStr = "";
            try {
                BufferedReader bfReader = null;
                //采用GB2312是因为歌词xml是以gb2312编码
                bfReader = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream(), "utf-8"));
                while ((str = bfReader.readLine()) != null) {
                    xmlStr += str;
                }
            } catch (FileNotFoundException e) {
                Toast.makeText(getActivity().getApplicationContext(), "未找到歌词", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return xmlStr;
        }

        /**
         * 从URL通过BufferedReader获取歌词信息
         *
         * @param httpURLConnection
         * @return
         */
        public ArrayList<String> getDataFromURL(HttpURLConnection httpURLConnection) {
            String str = "";
            ArrayList<String> lrcList = new ArrayList<>();
            try {
                BufferedReader bfReader = null;
                //采用GB2312是因为歌词xml是以gb2312编码
                bfReader = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream(), "GB2312"));
                while ((str = bfReader.readLine()) != null) {
                    str = getSingleLineLrcTime(str);
                    if (!str.equals("")) {
                        lrcList.add(str);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return lrcList;
        }

        /**
         * 获取单行歌词的时间戳和歌词内容
         *
         * @param str
         * @return
         */
        public String getSingleLineLrcTime(String str) {
            Pattern pattern = Pattern.compile("[0-9]{2}:[0-9]{2}.[0-9]{2}");
            str = str.substring(1);
            if (pattern.matcher(str).lookingAt()) {
                int index = 0;
                int middle = str.indexOf("]");
                String timeStr = str.substring(0, middle);//获取时间戳
                //会存在只有时间戳没有歌词的情况这个情况要把多余的时间戳舍去
                /**
                 * [00:20.41]
                 * [00:22.43]请你不要睡觉
                 * 这个时候需要将[00:20.41]舍去，保证歌词与时间戳一一对应
                 */
                //即除去只有时间戳的例子
                if (str.length() - 1 != middle) {
                    int totalTime = Integer.parseInt(timeStr.substring(0, 2)) * 60 * 1000 +
                            Integer.parseInt(timeStr.substring(3, 5)) * 1000 + Integer.parseInt(timeStr.substring(6, 8));//00:03.98
                    timeList.add(totalTime);
                }
                //最后一行
                if ((index = str.indexOf("]]></lyric>")) != -1) {
                    str = str.substring(middle + 1, index);
                }
                //其余行
                else {
                    str = str.substring(middle + 1);
                }
            } else {
                str = "";
            }
            return str;
        }
    }
}
