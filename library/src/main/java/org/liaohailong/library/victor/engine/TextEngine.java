package org.liaohailong.library.victor.engine;

import org.liaohailong.library.victor.Deliver;
import org.liaohailong.library.victor.request.Request;

import java.util.concurrent.PriorityBlockingQueue;

/**
 * Describe as: 文本数据请求引擎
 * Created by LiaoHaiLong on 2018/5/1.
 */

public class TextEngine implements IEngine {

    private final Deliver mDeliver;

    private final PriorityBlockingQueue<Request<?>> mCacheQueue;

    private final PriorityBlockingQueue<Request<?>> mNetworkQueue;

    private NetworkDispatcher[] mNetworkDispatchers;
    private CacheDispatcher mCacheDispatcher;
    private int mSize = 1;

    TextEngine(Deliver deliver, int size) {
        mDeliver = deliver;
        mCacheQueue = new PriorityBlockingQueue<>();
        mNetworkQueue = new PriorityBlockingQueue<>();
        mSize = size;
    }

    private void quiteAllDispatchers() {
        if (mNetworkDispatchers != null) {
            for (NetworkDispatcher networkDispatcher : mNetworkDispatchers) {
                if (networkDispatcher != null) {
                    networkDispatcher.quite();
                }
            }
            mNetworkDispatchers = null;
        }
        if (mCacheDispatcher != null) {
            mCacheDispatcher.quite();
            mCacheDispatcher = null;
        }
    }

    @Override
    public void start() {
        quiteAllDispatchers();
        mNetworkDispatchers = new NetworkDispatcher[mSize];
        for (NetworkDispatcher mNetworkDispatcher : mNetworkDispatchers) {
            mNetworkDispatcher = new NetworkDispatcher(mNetworkQueue, mDeliver);
            mNetworkDispatcher.start();
        }
        mCacheDispatcher = new CacheDispatcher(mNetworkQueue, mCacheQueue, mDeliver);
        mCacheDispatcher.start();
    }

    @Override
    public void addRequest(Request<?> request) {
        if (request.isShouldCache()) {
            mCacheQueue.add(request);
        } else {
            mNetworkQueue.add(request);
        }
    }

    @Override
    public void removeRequest(Request<?> request) {
        if (request.isShouldCache()) {
            mCacheQueue.remove(request);
        } else {
            mNetworkQueue.remove(request);
        }
    }

    @Override
    public void clearRequest() {
        mNetworkQueue.clear();
    }

    @Override
    public void release() {
        mNetworkQueue.clear();
        mCacheQueue.clear();
        quiteAllDispatchers();
    }
}
