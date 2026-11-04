package com.xfjy.server.interceptor;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * 包装HttpServletRequest
 */
public class MyServletRequestWrapper extends HttpServletRequestWrapper {

    /** 请求体缓存上限：1MB（防恶意大 body 全量读入内存导致 OOM） */
    private static final int MAX_BODY_SIZE = 1024 * 1024;

    private final byte[] body;

    /**
     * Construct a wrapper for the specified request.
     *
     * @param request The request to be wrapped
     * @throws IOException 请求体超过 {@link #MAX_BODY_SIZE} 或读取失败
     */
    public MyServletRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        // Content-Length 预检：快速拒绝声明超限的请求（chunked 无 Content-Length 时为 -1，走读取时硬上限）
        long contentLength = request.getContentLengthLong();
        if (contentLength > MAX_BODY_SIZE) {
            throw new IOException("请求体超过上限 " + MAX_BODY_SIZE + " bytes: " + contentLength);
        }
        // 读取时硬上限：防 chunked 传输绕过 Content-Length 预检
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0;
        try (InputStream in = super.getInputStream()) {
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > MAX_BODY_SIZE) {
                    throw new IOException("请求体超过上限 " + MAX_BODY_SIZE + " bytes");
                }
                bos.write(buf, 0, n);
            }
        }
        body = bos.toByteArray();
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream()));
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        return new RequestBodyCachingInputStream(body);
    }

    private class RequestBodyCachingInputStream extends ServletInputStream {
        private byte[] body;
        private int lastIndexRetrieved = -1;
        private ReadListener listener;

        public RequestBodyCachingInputStream(byte[] body) {
            this.body = body;
        }

        @Override
        public int read() throws IOException {
            if (isFinished()) {
                return -1;
            }
            int i = body[lastIndexRetrieved + 1];
            lastIndexRetrieved++;
            if (isFinished() && listener != null) {
                try {
                    listener.onAllDataRead();
                } catch (IOException e) {
                    listener.onError(e);
                    throw e;
                }
            }
            return i;
        }

        @Override
        public boolean isFinished() {
            return lastIndexRetrieved == body.length - 1;
        }

        @Override
        public boolean isReady() {
            // 数据已全部缓存在内存，永远可读、不会阻塞
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            if (listener == null) {
                throw new IllegalArgumentException("listener cann not be null");
            }
            if (this.listener != null) {
                throw new IllegalArgumentException("listener has been set");
            }
            this.listener = listener;
            // 仅当数据已全部读完时立即回调 onAllDataRead；
            // 未读完时等 read() 读到最后一个字节后再回调，避免容器误认为读取完成而丢弃请求体
            if (isFinished()) {
                try {
                    listener.onAllDataRead();
                } catch (IOException e) {
                    listener.onError(e);
                }
            }
        }

        @Override
        public int available() throws IOException {
            return body.length - lastIndexRetrieved - 1;
        }

        @Override
        public void close() throws IOException {
            lastIndexRetrieved = body.length - 1;
            body = null;
        }
    }
}