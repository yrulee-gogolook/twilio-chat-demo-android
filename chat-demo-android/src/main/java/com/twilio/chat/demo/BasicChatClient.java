package com.twilio.chat.demo;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;

import com.twilio.accessmanager.AccessManager;
import com.twilio.chat.CallbackListener;
import com.twilio.chat.ChatClient;
import com.twilio.chat.ErrorInfo;

public class BasicChatClient
{
    private static final Logger logger = Logger.getLogger(BasicChatClient.class);

    private String accessToken;
    private String gcmToken;

    private ChatClient chatClient;

    private Context       context;
    private AccessManager accessManager;

    private LoginListener loginListener;
    private Handler       loginListenerHandler;

    private String        urlString;
    private String        username;

    public BasicChatClient(Context context)
    {
        super();
        this.context = context;

        if (BuildConfig.DEBUG) {
            ChatClient.setLogLevel(android.util.Log.DEBUG);
        } else {
            ChatClient.setLogLevel(android.util.Log.ERROR);
        }
    }

    public interface LoginListener {
        public void onLoginStarted();

        public void onLoginFinished();

        public void onLoginError(String errorMessage);

        public void onLogoutFinished();
    }

    public String getGCMToken()
    {
        return gcmToken;
    }

    public void setGCMToken(String gcmToken)
    {
        this.gcmToken = gcmToken;
    }

    public void login(final String username, final String url, final LoginListener listener) {
        if (username == this.username && urlString == url && loginListener == listener && chatClient != null && accessManager != null) {
            handleSuccess(chatClient);
            return;
        }

        this.username = username;
        urlString = url;

        loginListenerHandler = setupListenerHandler();
        loginListener = listener;

        new GetAccessTokenAsyncTask().execute(username, urlString);
    }

    public ChatClient getChatClient()
    {
        return chatClient;
    }

    private void setupGcmToken()
    {
        chatClient.registerGCMToken(getGCMToken(),
            new ToastStatusListener(
                "GCM registration successful",
                "GCM registration not successful"));
    }

    private void createAccessManager()
    {
        if (accessManager != null) return;

        accessManager = new AccessManager(accessToken, new AccessManager.Listener() {
            @Override
            public void onTokenWillExpire(AccessManager accessManager)
            {
                TwilioApplication.get().showToast("AccessManager.onTokenWillExpire");
            }

            @Override
            public void onTokenExpired(AccessManager accessManager)
            {
                TwilioApplication.get().showToast("Token expired. Getting new token.");
                new GetAccessTokenAsyncTask().execute(username, urlString);
            }

            @Override
            public void onError(AccessManager accessManager, String err)
            {
                TwilioApplication.get().showToast("AccessManager error: " + err);
            }
        });
        accessManager.addTokenUpdateListener(new AccessManager.TokenUpdateListener() {
            @Override
            public void onTokenUpdated(String token)
            {
                TwilioApplication.get().showToast("AccessManager token updated: " + token);

                if (chatClient == null) return;

                chatClient.updateToken(token, new ToastStatusListener(
                        "Client Update Token was successful",
                        "Client Update Token failed"));
            }
        });
    }

    private void createClient()
    {
        if (chatClient != null) return;

        ChatClient.Properties props =
            new ChatClient.Properties.Builder()
                .setSynchronizationStrategy(ChatClient.SynchronizationStrategy.CHANNELS_LIST)
                .setRegion("us1")
                .createProperties();

        ChatClient.create(context.getApplicationContext(),
                accessToken,
                props,
                new CallbackListener<ChatClient>() {
                    // Client created, remember the reference and set up UI
                    @Override
                    public void onSuccess(ChatClient client)
                    {
                        handleSuccess(client);
                    }

                    // Client not created, fail
                    @Override
                    public void onError(final ErrorInfo errorInfo)
                    {
                        TwilioApplication.get().logErrorInfo("Received onError event", errorInfo);

                        loginListenerHandler.post(new Runnable() {
                            @Override
                            public void run()
                            {
                                if (loginListener != null) {
                                    loginListener.onLoginError(errorInfo.getErrorCode() + " " + errorInfo.getErrorText());
                                }
                            }
                        });
                    }
                });
    }

    private void handleSuccess(ChatClient client) {
        logger.d("Received completely initialized ChatClient");
        chatClient = client;

        setupGcmToken();

        loginListenerHandler.post(new Runnable() {
            @Override
            public void run()
            {
                if (loginListener != null) {
                    loginListener.onLoginFinished();
                }
            }
        });
    }

    public void shutdown()
    {
        chatClient.shutdown();
        chatClient = null; // Client no longer usable after shutdown()
    }

    private Handler setupListenerHandler()
    {
        Looper  looper;
        Handler handler;
        if ((looper = Looper.myLooper()) != null) {
            handler = new Handler(looper);
        } else if ((looper = Looper.getMainLooper()) != null) {
            handler = new Handler(looper);
        } else {
            handler = null;
            throw new IllegalArgumentException("Channel Listener must have a Looper.");
        }
        return handler;
    }

    /**
     * Modify this method if you need to provide more information to your Access Token Service.
     */
    private class GetAccessTokenAsyncTask extends AsyncTask<String, Void, String>
    {
        @Override
        protected void onPreExecute()
        {
            super.onPreExecute();
            loginListenerHandler.post(new Runnable() {
                @Override
                public void run()
                {
                    if (loginListener != null) {
                        loginListener.onLoginStarted();
                    }
                }
            });
        }

        @Override
        protected String doInBackground(String... params)
        {
            try {
                accessToken = HttpHelper.httpGet(params[0], params[1]);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return accessToken;
        }

        @Override
        protected void onPostExecute(String result)
        {
            super.onPostExecute(result);
            createAccessManager();
            createClient();
            accessManager.updateToken(accessToken);
        }
    }
}
