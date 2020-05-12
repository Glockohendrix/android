package mega.privacy.android.app.lollipop.megachat.calls;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import androidx.fragment.app.Fragment;
import mega.privacy.android.app.MegaApplication;
import mega.privacy.android.app.R;
import mega.privacy.android.app.components.RoundedImageView;
import nz.mega.sdk.MegaChatApiAndroid;
import nz.mega.sdk.MegaChatCall;
import nz.mega.sdk.MegaChatRoom;
import nz.mega.sdk.MegaChatSession;
import static mega.privacy.android.app.utils.CallUtil.*;
import static mega.privacy.android.app.utils.Constants.*;
import static mega.privacy.android.app.utils.LogUtil.*;
import static mega.privacy.android.app.utils.Util.*;
import static nz.mega.sdk.MegaChatApiJava.MEGACHAT_INVALID_HANDLE;

public class FragmentIndividualCall extends Fragment implements View.OnClickListener {

    private RelativeLayout muteLayout;
    private RelativeLayout videoLayout;
    private ImageView avatarImageOnHold;

    private IndividualCallListener listener = null;
    private Context context;
    private MegaChatApiAndroid megaChatApi;
    private Display display;
    private DisplayMetrics outMetrics;

    private View contentView;

    private long chatId;
    private long peerid;
    private long clientid;
    private boolean isSmallCamera;

    private SurfaceView surfaceView = null;
    private RelativeLayout avatarLayout;
    private RoundedImageView avatarImage;
    private MegaChatRoom chatRoom;

    public static FragmentIndividualCall newInstance(long chatId, long peerid, long clientid, boolean isSmallCamera) {
        logDebug("Chat ID: " + chatId);

        FragmentIndividualCall f = new FragmentIndividualCall();
        Bundle args = new Bundle();
        args.putLong(CHAT_ID, chatId);
        args.putLong(PEER_ID, peerid);
        args.putLong(CLIENT_ID, clientid);
        args.putBoolean(TYPE_CAMERA, isSmallCamera);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Bundle args = getArguments();
        this.chatId = args.getLong(CHAT_ID, MEGACHAT_INVALID_HANDLE);
        this.peerid = args.getLong(PEER_ID, INVALID_CALL_PEER_ID);
        this.clientid = args.getLong(CLIENT_ID, INVALID_CALL_CLIENT_ID);
        this.isSmallCamera = args.getBoolean(TYPE_CAMERA, false);

        megaChatApi = MegaApplication.getInstance().getMegaChatApi();
        this.chatRoom = megaChatApi.getChatRoom(chatId);
        if (chatRoom == null || megaChatApi.getChatCall(chatId) == null)
            return;

        if (peerid == INVALID_CALL_PEER_ID) {
            this.peerid = chatRoom.getPeerHandle(0);
        }

        display = ((ChatCallActivity) context).getWindowManager().getDefaultDisplay();
        outMetrics = new DisplayMetrics();
        display.getMetrics(outMetrics);

        logDebug("Chat ID: " + chatId);
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        if (!isAdded())
            return null;

        if (isSmallCamera) {
            contentView = inflater.inflate(R.layout.fragment_local_camera_call, container, false);
            videoLayout = contentView.findViewById(R.id.video_layout);
            surfaceView = contentView.findViewById(R.id.video);
            surfaceView.setVisibility(View.GONE);
            videoLayout.setVisibility(View.GONE);
            avatarLayout = contentView.findViewById(R.id.avatar_layout);
            avatarLayout.setVisibility(View.GONE);
            avatarImage = contentView.findViewById(R.id.avatar_image);
            avatarImage.setAlpha(1f);
            muteLayout = contentView.findViewById(R.id.mute_layout);
            muteLayout.setVisibility(View.GONE);

        } else {
            contentView = inflater.inflate(R.layout.full_screen_individual_call, container, false);
            surfaceView = contentView.findViewById(R.id.video);
            surfaceView.setOnClickListener(this);
            surfaceView.setVisibility(View.GONE);
            avatarLayout = contentView.findViewById(R.id.avatar_layout);
            avatarLayout.setOnClickListener(this);
            avatarLayout.setVisibility(View.GONE);
            avatarImage = contentView.findViewById(R.id.avatar_image);
            avatarImage.setAlpha(1f);

            avatarImageOnHold = contentView.findViewById(R.id.avatar_image_on_hold);
            avatarImageOnHold.setVisibility(View.GONE);
        }

        init();

        return contentView;
    }

    private void init() {
        updateAvatar(peerid);
        checkValues(peerid, clientid, null);
    }

    /**
     * Method for obtaining the session with the contact.
     *
     * @return The session.
     */
    private MegaChatSession getSession() {
        MegaChatCall call = ((ChatCallActivity)context).getCall();

        if (call == null || isItMe(chatId, peerid, clientid))
            return null;

        return call.getMegaChatSession(peerid, clientid);
    }

    /**
     * Method for loading the corresponding avatar.
     *
     * @param newPeerId Peer ID.
     */
    private void updateAvatar(long newPeerId) {
        if (peerid != newPeerId)
            return;

        chatRoom = megaChatApi.getChatRoom(chatId);

        /*Default Avatar*/
        Bitmap defaultBitmap = getDefaultAvatarCall(chatRoom, peerid, isSmallCamera, true);
        avatarImage.setImageBitmap(defaultBitmap);

        /*Avatar*/
        Bitmap bitmap = getImageAvatarCall(context, chatRoom, peerid);
        if (bitmap != null) {
            avatarImage.setImageBitmap(bitmap);
        }
    }

    /**
     * Method for updating the video status.
     *
     * @param peerId   Peer ID.
     * @param clientId Client ID.
     * @param session  The state of the session.
     */
    public void checkValues(long peerId, long clientId, MegaChatSession session) {
        MegaChatCall callChat = ((ChatCallActivity) context).getCall();
        if (callChat == null)
            return;

        if (peerId != peerid || clientId != clientid)
            return;

        if (isItMe(chatId, peerId, clientId)) {
            if (callChat.hasLocalVideo() && !callChat.isOnHold() && !((ChatCallActivity) context).isSessionOnHold()) {
                activateVideo();
                return;
            }

            showAvatar();
            return;
        }

        if (session != null && session.hasVideo() && !callChat.isOnHold() && !((ChatCallActivity) context).isSessionOnHold()) {
            activateVideo();
            return;
        }

        showAvatar();
        showMuteIcon(peerid, clientid);
    }

    /**
     * Method for activating the video.
     */
    private void activateVideo() {
        if (surfaceView == null || surfaceView.getVisibility() == View.VISIBLE)
            return;

        hideAvatar();

        if (listener == null) {
            listener = new IndividualCallListener(context, surfaceView, peerid, clientid, chatId, outMetrics, isSmallCamera);
            if (isItMe(chatId, peerid, clientid)) {
                megaChatApi.addChatLocalVideoListener(chatId, listener);
            } else {
                megaChatApi.addChatRemoteVideoListener(chatId, peerid, clientid, listener);
            }
        } else {
            listener.setHeight(0);
            listener.setWidth(0);
        }

        if (isSmallCamera) {
            videoLayout.setVisibility(View.VISIBLE);
        }
        surfaceView.setVisibility(View.VISIBLE);
    }

    /**
     * Method for deactivating the video.
     */
    private void deactivateVideo() {
        if (surfaceView == null || listener == null || surfaceView.getVisibility() == View.GONE) {
            return;
        }

        logDebug("Removing suface view");
        surfaceView.setVisibility(View.GONE);
        if (isSmallCamera) {
            videoLayout.setVisibility(View.GONE);
        }

        if (listener != null) {
            logDebug("Removing remote video listener");
            if (isItMe(chatId, peerid, clientid)) {
                megaChatApi.removeChatVideoListener(chatId, -1, -1, listener);
            } else {
                megaChatApi.removeChatVideoListener(chatId, peerid, clientid, listener);
            }
            listener = null;
        }

        if (isSmallCamera) {
            checkIndividualAudioCall();
        }
    }

    /**
     * Method for updating the muted call bar on individual calls.
     */
    public void checkIndividualAudioCall() {
        if (!isSmallCamera)
            return;

        if (((ChatCallActivity) context).isIndividualAudioCall()) {
            hideAvatar();
        } else {
            avatarLayout.setVisibility(View.VISIBLE);
        }

        showMuteIcon(peerid, clientid);
    }

    /**
     * Method to add the bitmap to the avatar.
     */
    public void setAvatar(long peerId, Bitmap bitmap) {
        if (!isItMe(chatId, peerid, clientid) && peerId == chatRoom.getPeerHandle(0) && bitmap != null && avatarImage != null) {
            avatarImage.setImageBitmap(bitmap);
        }
    }

    /**
     * Method to show the avatar.
     */
    private void showAvatar() {
        if (avatarLayout == null)
            return;


        deactivateVideo();
        showOnHoldImage();
    }

    /**
     * Method to show the call on hold image.
     */
    public void showOnHoldImage() {
        if (isSmallCamera)
            return;

        avatarLayout.setVisibility(View.VISIBLE);
        MegaChatCall call = ((ChatCallActivity)context).getCall();

        if ((call != null && call.isOnHold()) || (getSession() != null && getSession().isOnHold())) {
            avatarImageOnHold.setVisibility(View.VISIBLE);
            avatarImage.setAlpha(0.5f);
        } else {
            avatarImageOnHold.setVisibility(View.GONE);
            avatarImage.setAlpha(1f);
        }
    }

    /**
     * Method to hide the avatar.
     */
    private void hideAvatar() {
        if (avatarLayout.getVisibility() == View.GONE)
            return;

        if (!isSmallCamera) {
            avatarImageOnHold.setVisibility(View.GONE);
            avatarImage.setAlpha(1f);
        }

        avatarLayout.setVisibility(View.GONE);
    }

    /**
     * Method to show the mute icon.
     */
    public void showMuteIcon(long peerid, long clientid) {
        if (!isSmallCamera || peerid != this.peerid || clientid != clientid || muteLayout == null)
            return;

        MegaChatCall call = ((ChatCallActivity) context).getCall();

        boolean isShouldShown = call != null && !call.isOnHold() &&
                !((ChatCallActivity) context).isSessionOnHold() && !call.hasLocalAudio() &&
                (surfaceView.getVisibility() == View.VISIBLE || avatarLayout.getVisibility() == View.VISIBLE);

        if (isShouldShown) {
            int marginTop;
            RelativeLayout.LayoutParams paramsMicroSurface = new RelativeLayout.LayoutParams(muteLayout.getLayoutParams());
            if (surfaceView.getVisibility() == View.VISIBLE) {
                paramsMicroSurface.addRule(RelativeLayout.ALIGN_RIGHT, videoLayout.getId());
                paramsMicroSurface.addRule(RelativeLayout.ALIGN_TOP, videoLayout.getId());
                marginTop = px2dp(12, outMetrics);
            } else {
                paramsMicroSurface.addRule(RelativeLayout.ALIGN_RIGHT, avatarLayout.getId());
                paramsMicroSurface.addRule(RelativeLayout.ALIGN_TOP, avatarLayout.getId());
                marginTop = px2dp(3, outMetrics);

            }
            paramsMicroSurface.setMargins(0, marginTop, px2dp(4, outMetrics), 0);
            muteLayout.setLayoutParams(paramsMicroSurface);
            muteLayout.setVisibility(View.VISIBLE);

            return;
        }

        muteLayout.setVisibility(View.GONE);
    }

    /**
     * Method for changing the user being displayed.
     *
     * @param newChatId   Chat ID.
     * @param callId      Call ID.
     * @param newPeerId   Peer ID.
     * @param newClientId Client ID.
     * @param session     The session with the contact.
     */
    public void changeUser(long newChatId, long callId, long newPeerId, long newClientId, MegaChatSession session) {
        logDebug(" Current peerI: " + peerid + ", new peerId = " + newPeerId + ". Current clientId: " + clientid + ", new clientId = " + newClientId);

        if (isSmallCamera || (newPeerId == peerid && newClientId == clientid) || ((ChatCallActivity)context).getCall().getId() != callId)
            return;

        deactivateVideo();

        this.peerid = newPeerId;
        this.clientid = newClientId;
        this.isSmallCamera = false;
        if (newChatId != chatId) {
            this.chatId = newChatId;
            this.chatRoom = megaChatApi.getChatRoom(chatId);
        }

        updateAvatar(peerid);
        checkValues(peerid, clientid, session);
    }


    /**
     * Method to destroy the surfaceView.
     */
    private void removeSurfaceView() {
        if (surfaceView != null) {
            if (surfaceView.getParent() != null && surfaceView.getParent().getParent() != null) {
                logDebug("Removing suface view");
                ((ViewGroup) surfaceView.getParent()).removeView(surfaceView);
            }

            surfaceView.setVisibility(View.GONE);
            if (isSmallCamera) {
                videoLayout.setVisibility(View.GONE);
            }
        }
        if (listener != null) {
            logDebug("Removing remote video listener");
            if (isItMe(chatId, peerid, clientid)) {
                megaChatApi.removeChatVideoListener(chatId, -1, -1, listener);
            } else {
                megaChatApi.removeChatVideoListener(chatId, peerid, clientid, listener);
            }
            listener = null;
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public void onClick(View v) {
        ((ChatCallActivity) context).remoteCameraClick();
    }

    @Override
    public void onResume() {
        if (listener != null) {
            listener.setHeight(0);
            listener.setWidth(0);
        }
        super.onResume();
    }

    @Override
    public void onDestroy() {
        removeSurfaceView();
        this.avatarImage.setImageBitmap(null);
        super.onDestroy();
    }
}
