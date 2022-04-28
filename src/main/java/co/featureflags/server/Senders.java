package co.featureflags.server;

import co.featureflags.server.exterior.DefaultSender;
import co.featureflags.server.exterior.HttpConfig;
import co.featureflags.server.exterior.InsightEventSender;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.time.Duration;

abstract class Senders {

    private static OkHttpClient buildWebOkHttpClient(HttpConfig httpConfig) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.connectTimeout(httpConfig.connectTime()).readTimeout(httpConfig.socketTime()).writeTimeout(httpConfig.socketTime()).retryOnConnectionFailure(false);
        Utils.buildProxyAndSocketFactoryFor(builder, httpConfig);
        return builder.build();
    }

    static class DefaultSenderImp implements DefaultSender {
        private static final MediaType JSON_CONTENT_TYPE = MediaType.parse("application/json; charset=utf-8");

        private final HttpConfig httpConfig;
        private final OkHttpClient okHttpClient;
        private final Integer maxRetryTimes;
        private final Duration retryInterval;

        DefaultSenderImp(HttpConfig httpConfig, Integer maxRetryTimes, Duration retryInterval) {
            this.httpConfig = httpConfig;
            this.maxRetryTimes = maxRetryTimes;
            this.retryInterval = retryInterval;
            this.okHttpClient = buildWebOkHttpClient(httpConfig);
        }

        @Override
        public String postJson(String url, String jsonBody) {

            RequestBody body = RequestBody.create(jsonBody, JSON_CONTENT_TYPE);
            Headers headers = Utils.headersBuilderFor(httpConfig).build();
            Request request = new Request.Builder().headers(headers).url(url).post(body).build();

            for (int i = 0; i < maxRetryTimes + 1; i++) {
                if (i > 0) {
                    try {
                        Thread.sleep(retryInterval.toMillis());
                    } catch (InterruptedException ignore) {
                    }
                }

                try (Response response = okHttpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        return response.body().string();
                    }
                } catch (Exception ignore) {
                }
            }
            return null;
        }

        @Override
        public void close() throws IOException {
            Utils.shutdownOKHttpClient("post sender", okHttpClient);
        }
    }

    static class InsightEventSenderImp implements InsightEventSender {

        private static final MediaType JSON_CONTENT_TYPE = MediaType.parse("application/json; charset=utf-8");

        private final HttpConfig httpConfig;
        private final OkHttpClient okHttpClient;
        private final Integer maxRetryTimes;
        private final Duration retryInterval;

        InsightEventSenderImp(HttpConfig httpConfig, Integer maxRetryTimes, Duration retryInterval) {
            this.httpConfig = httpConfig;
            this.maxRetryTimes = maxRetryTimes;
            this.retryInterval = retryInterval;
            this.okHttpClient = buildWebOkHttpClient(httpConfig);
        }

        @Override
        public void sendEvent(String eventUrl, String json) {
            Loggers.EVENTS.trace("events: {}", json);
            RequestBody body = RequestBody.create(json, JSON_CONTENT_TYPE);
            Headers headers = Utils.headersBuilderFor(httpConfig).build();
            Request request = new Request.Builder().headers(headers).url(eventUrl).post(body).build();

            Loggers.EVENTS.debug("Sending events...");
            for (int i = 0; i < maxRetryTimes + 1; i++) {
                if (i > 0) {
                    try {
                        Thread.sleep(retryInterval.toMillis());
                    } catch (InterruptedException ignore) {
                    }
                }

                try (Response response = okHttpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        Loggers.EVENTS.debug("sending events ok");
                        return;
                    }
                } catch (Exception ex) {
                    Loggers.EVENTS.error("FFC JAVA SDK: events sending error: {}", ex.getMessage());
                }
            }
        }

        @Override
        public void close() {
            Utils.shutdownOKHttpClient("insight event sender", okHttpClient);
        }
    }
}
