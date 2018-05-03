package org.liaohailong.library.victor;

import android.content.Context;
import android.support.annotation.MainThread;
import android.text.TextUtils;
import android.widget.Toast;

import org.liaohailong.library.victor.callback.Callback;
import org.liaohailong.library.victor.engine.EngineManager;
import org.liaohailong.library.victor.engine.FileEngine;
import org.liaohailong.library.victor.engine.IEngine;
import org.liaohailong.library.victor.interceptor.Interceptor;
import org.liaohailong.library.victor.request.FileRequest;
import org.liaohailong.library.victor.request.Request;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Describe as: 基于HttpUrlConnection封装的网络请求库
 * 基本功能：
 * 1，上传文件、下载文件（多线程断点下载）             (未完成)
 * 2，仿Volley的万箭齐发式请求（轻量级任务）
 * 3，手动移除网络队列中的任务（文件下载/上传除外）
 * 4，数据缓存，减少请求网络的频率，从而优化流量费用
 * 5，自由修改全局统一的Http请求首部字段
 * 6，全局统一的拦截器
 * Created by LiaoHaiLong on 2018/4/30.
 */

public class Victor {
    //全局基本配置
    private VictorConfig mVictorConfig;
    //数据跨线程传送门
    private Deliver mDeliver = new Deliver();
    //双引擎控制器
    private EngineManager mEngineManager = new EngineManager(mDeliver);

    private static final class SingletonHolder {
        static final Victor INSTANCE = new Victor();
    }

    public static Victor getInstance() {
        return SingletonHolder.INSTANCE;
    }

    @MainThread
    public VictorConfig initConfig(Context context) {
        if (mVictorConfig == null) {
            mVictorConfig = new VictorConfig(context);
        }
        return mVictorConfig;
    }

    public VictorConfig getConfig() {
        return mVictorConfig;
    }

    public EngineManager getEngineManager() {
        return mEngineManager;
    }

    public LinkedList<Interceptor> getInterceptors() {
        return mVictorConfig.getInterceptors();
    }

    public TextRequestBuilder newTextRequest() {
        return new TextRequestBuilder();
    }

    public DownloadFileRequestBuilder newDownloadRequest() {
        return new DownloadFileRequestBuilder();
    }

    private DownloadFileRequestBuilder newMultipleDownloadRequest() {
        return new DownloadFileRequestBuilder();
    }

    public UploadFileRequestBuilder newUploadRequest() {
        return new UploadFileRequestBuilder();
    }

    private UploadFileRequestBuilder newMultipleUploadRequest() {
        return new UploadFileRequestBuilder();
    }

    public void release() {
        mEngineManager.flameOut();
    }

    public abstract class RequestBuilder {
        private String url;
        private String httpMethod = HttpInfo.GET;
        private Map<String, String> headers = new HashMap<>();
        private Map<String, String> params = new HashMap<>();
        private RequestPriority requestPriority = RequestPriority.MIDDLE;
        private int connectTimeOut = 0;
        private int readTimeOut = 0;
        private IEngine mEngine;
        private boolean useCache = false;
        private boolean useCookie = false;

        public RequestBuilder setUrl(String url) {
            this.url = url;
            return this;
        }

        public RequestBuilder doGet() {
            httpMethod = HttpInfo.GET;
            return this;
        }

        public RequestBuilder doPost() {
            httpMethod = HttpInfo.POST;
            return this;
        }

        public RequestBuilder addHeader(String header, String value) {
            headers.put(header, value);
            return this;
        }

        public RequestBuilder addHeaders(Map<String, String> headers) {
            this.headers.putAll(headers);
            return this;
        }

        public RequestBuilder addParam(String key, String value) {
            params.put(key, value);
            return this;
        }

        public RequestBuilder addParams(Map<String, String> params) {
            this.params.putAll(params);
            return this;
        }

        public RequestBuilder setRequestPriority(RequestPriority requestPriority) {
            this.requestPriority = requestPriority;
            return this;
        }

        public RequestBuilder setConnectTimeOut(int connectTimeOut) {
            this.connectTimeOut = connectTimeOut;
            return this;
        }

        public RequestBuilder setReadTimeOut(int readTimeOut) {
            this.readTimeOut = readTimeOut;
            return this;
        }

        public RequestBuilder setUseCache(boolean useCache) {
            this.useCache = useCache;
            return this;
        }

        public RequestBuilder setUseCookie(boolean useCookie) {
            this.useCookie = useCookie;
            return this;
        }

        public <T> Request<T> setCallback(Callback<T> callback) {
            if (TextUtils.isEmpty(url)) {
                throw new IllegalArgumentException(" url can not be empty!");
            }
            int order = (int) System.currentTimeMillis();

            Map<String, String> defaultHeaders = mVictorConfig.getDefaultHeaders();
            Map<String, String> defaultParams = mVictorConfig.getDefaultParams();

            //过滤空的Http报文头部和首部字段
            Map<String, String> tempMap = new HashMap<>();
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (TextUtils.isEmpty(key) || TextUtils.isEmpty(value)) {
                    continue;
                }
                tempMap.put(key, value);
            }
            headers.clear();
            headers.putAll(tempMap);

            tempMap.clear();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (TextUtils.isEmpty(key) || TextUtils.isEmpty(value)) {
                    continue;
                }
                tempMap.put(key, value);
            }
            params.clear();
            params.putAll(tempMap);

            HttpField httpHeader = new HttpField()
                    .addParams(defaultHeaders)
                    .addParams(headers);

            HttpField httpParams = new HttpField()
                    .addParams(defaultParams)
                    .addParams(params);

            HttpConnectSetting mDefaultHttpConnectSetting = mVictorConfig.getHttpConnectSetting();
            HttpConnectSetting httpConnectSetting = new HttpConnectSetting()
                    .setConnectTimeout(connectTimeOut > 0 ? connectTimeOut : mDefaultHttpConnectSetting.getConnectTimeout())
                    .setReadTimeout(readTimeOut > 0 ? readTimeOut : mDefaultHttpConnectSetting.getReadTimeout());

            if (mEngine == null) {
                mEngine = getEngine();
                mEngine.start();
            }

            Request<T> request = getRequest(
                    requestPriority,
                    order,
                    useCache,
                    useCookie,
                    url,
                    httpMethod,
                    httpHeader,
                    httpParams,
                    httpConnectSetting,
                    callback,
                    mEngine);

            //判断是否存在网络连接情况
            Context applicationContext = getConfig().getApplicationContext();
            if (!Util.isNetEnable(applicationContext)) {
                Toast.makeText(applicationContext, "网络异常，请检测网络是否连接成功", Toast.LENGTH_LONG).show();
                return request;
            }

            mEngine.addRequest(request);
            return request;
        }

        protected abstract IEngine getEngine();

        protected <T> Request<T> getRequest(RequestPriority requestPriority,
                                            int order,
                                            boolean shouldCache,
                                            boolean shouldCookie,
                                            String url,
                                            String httpMethod,
                                            HttpField httpHeader,
                                            HttpField httpParams,
                                            HttpConnectSetting httpConnectSetting,
                                            Callback<T> callback,
                                            IEngine engine) {
            return new Request<>(requestPriority,
                    order,
                    shouldCache,
                    shouldCookie,
                    url,
                    httpMethod,
                    httpHeader,
                    httpParams,
                    httpConnectSetting,
                    callback,
                    engine);
        }
    }


    public final class TextRequestBuilder extends RequestBuilder {

        @Override
        protected IEngine getEngine() {
            return mEngineManager.getTextEngine();
        }
    }

    public class DownloadFileRequestBuilder extends RequestBuilder {
        private boolean isMultiple = false;

        @Override
        public DownloadFileRequestBuilder setUrl(String url) {
            super.setUrl(url);
            return this;
        }

        @Override
        protected IEngine getEngine() {
            FileEngine fileEngine = mEngineManager.getFileEngine();
            fileEngine.start();
            return fileEngine;
        }

        public DownloadFileRequestBuilder setMultiple(boolean multiple) {
            isMultiple = multiple;
            return this;
        }

        @Override
        protected <T> Request<T> getRequest(RequestPriority requestPriority,
                                            int order,
                                            boolean shouldCache,
                                            boolean shouldCookie,
                                            String url,
                                            String httpMethod,
                                            HttpField httpHeader,
                                            HttpField httpParams,
                                            HttpConnectSetting httpConnectSetting,
                                            Callback<T> callback, IEngine engine) {
            FileRequest<T> tRequest = new FileRequest<>(requestPriority,
                    order,
                    shouldCache,
                    shouldCookie,
                    url,
                    httpMethod,
                    httpHeader,
                    httpParams,
                    httpConnectSetting,
                    callback,
                    engine);
            tRequest.setMultiple(isMultiple);
            tRequest.setDownload(true);
            return tRequest;
        }
    }

    public final class UploadFileRequestBuilder extends DownloadFileRequestBuilder {
        private String key;
        private File file;

        @Override
        public UploadFileRequestBuilder setUrl(String url) {
            super.setUrl(url);
            return this;
        }

        public UploadFileRequestBuilder addFile(String key, File file) {
            this.key = key;
            this.file = file;
            return this;
        }

        @Override
        public RequestBuilder doGet() {
            throw new IllegalArgumentException(getClass().getSimpleName() + "  can not be a GET request");
        }

        @Override
        protected <T> Request<T> getRequest(RequestPriority requestPriority,
                                            int order,
                                            boolean shouldCache,
                                            boolean shouldCookie,
                                            String url,
                                            String httpMethod,
                                            HttpField httpHeader,
                                            HttpField httpParams,
                                            HttpConnectSetting httpConnectSetting,
                                            Callback<T> callback,
                                            IEngine engine) {

            if (TextUtils.isEmpty(key)) {
                throw new IllegalArgumentException("UploadFileRequestBuilder:key can not be empty!");
            }
            if (file == null) {
                throw new IllegalArgumentException("UploadFileRequestBuilder:file can not be empty!");
            }
            if (!file.exists()) {
                throw new IllegalArgumentException("UploadFileRequestBuilder:file is not exist");
            }
            if (file.isDirectory()) {
                throw new IllegalArgumentException("UploadFileRequestBuilder:file is isDirectory ? are you kidding me ?");
            }

            FileRequest<T> request = (FileRequest<T>) super.getRequest(requestPriority,
                    order,
                    shouldCache,
                    shouldCookie,
                    url,
                    HttpInfo.POST,
                    httpHeader,
                    httpParams,
                    httpConnectSetting,
                    callback,
                    engine);
            request.addFiles(key, file);
            request.setDownload(false);
            return request;
        }
    }
}

