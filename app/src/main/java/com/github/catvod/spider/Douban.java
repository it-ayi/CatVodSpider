package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Douban extends Spider {

    private final String siteUrl = "https://frodo.douban.com/api/v2";
    private final String apikey = "?apikey=0ac44ae016490db2204ce0a042db2916";
    private String extend;
    // 存储设备的Android ID和私钥
    private String androidId;
    private PrivateKey privateKey;


    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("Host", "frodo.douban.com");
        header.put("Connection", "Keep-Alive");
        header.put("Referer", "https://servicewechat.com/wx2f9b06c1de1ccfca/84/page-frame.html");
        header.put("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.143 Safari/537.36 MicroMessenger/7.0.9.501 NetType/WIFI MiniProgramEnv/Windows WindowsWechat");
        return header;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        this.extend = extend;
    }



    // 初始化方法，获取Android ID并执行登录流程
    @Override
    public void init(Context context) {
        try {
            // 尝试获取设备的Android ID
            androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);

            // 如果androidId为空，从缓存中获取或生成一个新的ID
            if (androidId == null) {
                androidId = getFromCache();
                if (androidId == null) {
                    androidId = UUID.randomUUID().toString();
                    storeInCache(androidId);
                }
            }

            // 获取私钥
            fetchPrivateKey();

            // 发送POST请求进行身份验证
            this.sendPost();
        } catch (Exception e) {
            System.err.println("发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }


    /////////////////////////////////////

    private void fetchPrivateKey() throws Exception {
        HttpURLConnection connection = null;
        try {
            // 创建HTTP连接请求私钥
            URL url = new URL("https://app.ysctv.cn/API/getPrivateKey.php");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            // 获取服务器响应并解析私钥
            String keyPEM = getResponse(connection);
            keyPEM = keyPEM.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");

            // 解码私钥并生成PrivateKey对象
            byte[] decodedKey = Base64.getDecoder().decode(keyPEM);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decodedKey);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            this.privateKey = keyFactory.generatePrivate(spec);

        } finally {
            // 断开连接
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // 生成签名
    public String generateSignature(String data) throws Exception {
        // 创建Signature对象，使用私钥生成签名
        Signature privateSignature = Signature.getInstance("SHA256withRSA");
        privateSignature.initSign(privateKey);
        privateSignature.update(data.getBytes("UTF-8"));

        // 返回Base64编码的签名
        byte[] signature = privateSignature.sign();
        return Base64.getEncoder().encodeToString(signature);
    }

    // 发送POST请求以进行身份验证
    public void sendPost() {
        HttpURLConnection postConn = null;
        try {
            // 创建HTTP连接请求进行身份验证
            URL postUrl = new URL("https://app.ysctv.cn/Authn.php");
            postConn = (HttpURLConnection) postUrl.openConnection();
            postConn.setRequestMethod("POST");
            postConn.setDoOutput(true);
            postConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            // 准备请求参数
            long timestamp = System.currentTimeMillis();
            String rawData = "timestamp=" + timestamp + "&androidId=" + androidId;
            String signature = generateSignature(rawData);
            String urlParameters = rawData + "&signature=" + signature;

            // 发送请求
            try (OutputStream os = postConn.getOutputStream()) {
                os.write(urlParameters.getBytes("UTF-8"));
                os.flush();
            }

            // 处理服务器响应
            String response = getResponse(postConn);
            System.out.println("服务器响应: " + response);

        } catch (Exception e) {
            System.err.println("发送POST请求失败");
            e.printStackTrace();
        } finally {
            // 断开连接
            if (postConn != null) {
                postConn.disconnect();
            }
        }
    }

    // 获取HTTP连接的响应内容
    private String getResponse(HttpURLConnection conn) throws Exception {
        StringBuilder content = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            String inputLine;
            // 逐行读取响应内容
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
        }
        return content.toString(); // 返回响应内容
    }

    // 从缓存获取 androidId
    private String getFromCache() {
        try {
            URL url = new URL("http://127.0.0.1:9978/cache?do=get&key=androidId");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            String response = getResponse(conn);
            if (!response.isEmpty()) {
                return response;
            }
        } catch (Exception e) {
            System.err.println("无法从缓存获取androidId: " + e.getMessage());
        }
        return null;
    }

    // 将生成的UUID存储到缓存中
    private void storeInCache(String uuid) {
        try {
            URL url = new URL("http://127.0.0.1:9978/cache?do=set&key=androidId&value=" + uuid);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.getInputStream().close(); // 执行请求
        } catch (Exception e) {
            System.err.println("无法将androidId存储到缓存中: " + e.getMessage());
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////
    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        List<String> typeIds = Arrays.asList("hot_gaia", "tv_hot", "show_hot", "movie", "tv", "rank_list_movie", "rank_list_tv");
        List<String> typeNames = Arrays.asList("热门电影", "热播剧集", "热播综艺", "电影筛选", "电视筛选", "电影榜单", "电视剧榜单");
        for (int i = 0; i < typeIds.size(); i++) classes.add(new Class(typeIds.get(i), typeNames.get(i)));
        String recommendUrl = "http://api.douban.com/api/v2/subject_collection/subject_real_time_hotest/items" + apikey;
        JSONObject jsonObject = new JSONObject(OkHttp.string(recommendUrl, getHeader()));
        JSONArray items = jsonObject.optJSONArray("subject_collection_items");
        return Result.string(classes, parseVodListFromJSONArray(items), filter ? Json.parse(OkHttp.string(extend)) : null);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String sort = extend.get("sort") == null ? "T" : extend.get("sort");
        String tags = URLEncoder.encode(getTags(extend));
        int start = (Integer.parseInt(pg) - 1) * 20;
        String cateUrl;
        String itemKey = "items";
        switch (tid) {
            case "hot_gaia":
                sort = extend.get("sort") == null ? "recommend" : extend.get("sort");
                String area = extend.get("area") == null ? "全部" : extend.get("area");
                sort = sort + "&area=" + URLEncoder.encode(area);
                cateUrl = siteUrl + "/movie/hot_gaia" + apikey + "&sort=" + sort + "&start=" + start + "&count=20";
                break;
            case "tv_hot":
                String type = extend.get("type") == null ? "tv_hot" : extend.get("type");
                cateUrl = siteUrl + "/subject_collection/" + type + "/items" + apikey + "&start=" + start + "&count=20";
                itemKey = "subject_collection_items";
                break;
            case "show_hot":
                String showType = extend.get("type") == null ? "show_hot" : extend.get("type");
                cateUrl = siteUrl + "/subject_collection/" + showType + "/items" + apikey + "&start=" + start + "&count=20";
                itemKey = "subject_collection_items";
                break;
            case "tv":
                cateUrl = siteUrl + "/tv/recommend" + apikey + "&sort=" + sort + "&tags=" + tags + "&start=" + start + "&count=20";
                break;
            case "rank_list_movie":
                String rankMovieType = extend.get("榜单") == null ? "movie_real_time_hotest" : extend.get("榜单");
                cateUrl = siteUrl + "/subject_collection/" + rankMovieType + "/items" + apikey + "&start=" + start + "&count=20";
                itemKey = "subject_collection_items";
                break;
            case "rank_list_tv":
                String rankTVType = extend.get("榜单") == null ? "tv_real_time_hotest" : extend.get("榜单");
                cateUrl = siteUrl + "/subject_collection/" + rankTVType + "/items" + apikey + "&start=" + start + "&count=20";
                itemKey = "subject_collection_items";
                break;
            default:
                cateUrl = siteUrl + "/movie/recommend" + apikey + "&sort=" + sort + "&tags=" + tags + "&start=" + start + "&count=20";
                break;
        }
        JSONObject object = new JSONObject(OkHttp.string(cateUrl, getHeader()));
        JSONArray array = object.getJSONArray(itemKey);
        List<Vod> list = parseVodListFromJSONArray(array);
        int page = Integer.parseInt(pg), count = Integer.MAX_VALUE, limit = 20, total = Integer.MAX_VALUE;
        return Result.get().vod(list).page(page, count, limit, total).string();
    }

    private List<Vod> parseVodListFromJSONArray(JSONArray items) throws Exception {
        List<Vod> list = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String vodId = "msearch:" + item.optString("id");
            String name = item.optString("title");
            String pic = getPic(item);
            String remark = getRating(item);
            list.add(new Vod(vodId, name, pic, remark));
        }
        return list;
    }

    private String getRating(JSONObject item) {
        try {
            return "评分：" + item.getJSONObject("rating").optString("value");
        } catch (Exception e) {
            return "";
        }
    }

    private String getPic(JSONObject item) {
        try {
            return item.getJSONObject("pic").optString("normal") + "@Referer=https://api.douban.com/@User-Agent=" + Util.CHROME;
        } catch (Exception e) {
            return "";
        }
    }

    private String getTags(HashMap<String, String> extend) {
        try {
            StringBuilder tags = new StringBuilder();
            for (String key : extend.keySet()) if (!key.equals("sort")) tags.append(extend.get(key)).append(",");
            return Util.substring(tags.toString());
        } catch (Exception e) {
            return "";
        }
    }
}
