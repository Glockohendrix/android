package mega.privacy.android.app.lollipop.megachat.calls;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import android.util.DisplayMetrics;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import java.util.ArrayList;

import mega.privacy.android.app.MegaApplication;
import mega.privacy.android.app.R;
import mega.privacy.android.app.components.RoundedImageView;
import mega.privacy.android.app.lollipop.listeners.GroupCallListener;
import nz.mega.sdk.MegaApiAndroid;
import nz.mega.sdk.MegaChatApiAndroid;
import nz.mega.sdk.MegaChatCall;
import nz.mega.sdk.MegaChatRoom;
import nz.mega.sdk.MegaChatSession;
import jp.wasabeef.blurry.Blurry;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.widget.RelativeLayout.TRUE;
import static mega.privacy.android.app.utils.AvatarUtil.getRadius;
import static mega.privacy.android.app.utils.CallUtil.*;
import static mega.privacy.android.app.utils.LogUtil.*;
import static mega.privacy.android.app.utils.Util.*;
import static mega.privacy.android.app.utils.Constants.*;
import static nz.mega.sdk.MegaChatApiJava.MEGACHAT_INVALID_HANDLE;

public class GroupCallAdapter extends RecyclerView.Adapter<GroupCallAdapter.ViewHolderGroupCall> implements MegaSurfaceRendererGroup.MegaSurfaceRendererGroupListener {

    private static final int MARGIN_MUTE_ICON_SMALL = 4;
    private static final int MARGIN_MUTE_ICON_LARGE = 16;
    private static final int SIZE_MUTE_ICON_LARGE = 24;
    private static final int MARGIN_BUTTONS_SMALL = 150;
    private static final int SIZE_VIDEO_PARTICIPANTS = 90;
    private static final int SIZE_BIG_AVATAR = 88;
    private static final int SIZE_SMALL_AVATAR = 60;
    private static final int CHECK_PARTICIPANTS_UI = 3;
    
    private ViewHolderGroupCallGrid holderGrid = null;
    private Context context;
    private MegaApiAndroid megaApi;
    private MegaChatApiAndroid megaChatApi = null;
    private Display display;
    private DisplayMetrics outMetrics;
    private RecyclerView recyclerViewFragment;
    private ArrayList<InfoPeerGroupCall> peers;
    private long chatId;
    private int maxScreenWidth, maxScreenHeight;
    private boolean isManualMode = false;
    private MegaChatRoom chatRoom;
    private int statusBarHeight = 0;

    public GroupCallAdapter(Context context, RecyclerView recyclerView, ArrayList<InfoPeerGroupCall> peers, long chatId) {

        this.context = context;
        this.recyclerViewFragment = recyclerView;
        this.peers = peers;
        this.chatId = chatId;

        if (megaApi == null) {
            megaApi = MegaApplication.getInstance().getMegaApi();
        }

        if (megaApi != null) {
            logDebug("retryPendingConnections");
            megaApi.retryPendingConnections();
        }

        if (megaChatApi == null) {
            megaChatApi = MegaApplication.getInstance().getMegaChatApi();
        }

        chatRoom = megaChatApi.getChatRoom(chatId);
    }

    @Override
    public GroupCallAdapter.ViewHolderGroupCall onCreateViewHolder(ViewGroup parent, int viewType) {

        display = ((ChatCallActivity) context).getWindowManager().getDefaultDisplay();
        outMetrics = new DisplayMetrics();
        display.getMetrics(outMetrics);

        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_camera_group_call, parent, false);

        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            statusBarHeight = context.getResources().getDimensionPixelSize(resourceId);
        }

        maxScreenHeight = outMetrics.heightPixels - statusBarHeight;
        maxScreenWidth = outMetrics.widthPixels;

        holderGrid = new ViewHolderGroupCallGrid(v);

        holderGrid.rlGeneral = v.findViewById(R.id.general);
        holderGrid.greenLayer = v.findViewById(R.id.green_layer);
        holderGrid.videoLayout = v.findViewById(R.id.video_layout);

        holderGrid.parentSurfaceView = v.findViewById(R.id.parent_surface_view);
        holderGrid.avatarLayout = v.findViewById(R.id.avatar_layout);

        holderGrid.avatarBackground = v.findViewById(R.id.avatar_background);
        holderGrid.muteIconLayout = v.findViewById(R.id.mute_layout);
        holderGrid.muteIcon = v.findViewById(R.id.mute_icon);
        holderGrid.qualityBackground = v.findViewById(R.id.rl_quality);
        holderGrid.qualityBackground.setVisibility(View.GONE);
        holderGrid.qualityIcon = v.findViewById(R.id.quality_icon);
        holderGrid.qualityIcon.setVisibility(View.GONE);
        holderGrid.avatarImage = v.findViewById(R.id.avatar_image);
        holderGrid.avatarImageCallOnHold = v.findViewById(R.id.avatar_image_on_hold);
        holderGrid.avatarImageCallOnHold.setVisibility(View.GONE);
        holderGrid.avatarImage.setAlpha(1f);

        v.setTag(holderGrid);
        return holderGrid;
    }

    @Override
    public void onBindViewHolder(ViewHolderGroupCall holder, int position) {
        ViewHolderGroupCallGrid holderGrid2 = (ViewHolderGroupCallGrid) holder;
        onBindViewHolderGrid(holderGrid2, position);
    }

    /**
     * Method to get the holder.
     *
     * @param position Position in the adapter.
     * @return The GroupCallAdapter.ViewHolderGroupCall in this position.
     */
    private ViewHolderGroupCall getHolder(int position) {
        return (ViewHolderGroupCall) recyclerViewFragment.findViewHolderForAdapterPosition(position);
    }

    private ViewHolderGroupCall callOrHolderNull(int position,  ViewHolderGroupCall holder){
        MegaChatCall call = ((ChatCallActivity) context).getCall();
        if (call == null)
            return null;

        if (holder == null) {
            holder = getHolder(position);
        }

        return holder;
    }

    private void onBindViewHolderGrid(final ViewHolderGroupCallGrid holder, final int position) {
        final InfoPeerGroupCall peer = getNodeAt(position);
        MegaChatCall call = ((ChatCallActivity) context).getCall();
        if (peer == null || call == null) {
            return;
        }

        holder.peerId = peer.getPeerId();
        int numPeersOnCall = getItemCount();
        logDebug("Number of participants in the call: " + numPeersOnCall);

        refreshRecycler(position, holder, peer);

        if (isEstablishedCall(chatId)) {
            holder.rlGeneral.setOnClickListener(v -> {
                if (getItemCount() <= MAX_PARTICIPANTS_GRID) {
                    ((ChatCallActivity) context).remoteCameraClick();
                } else {
                    ((ChatCallActivity) context).itemClicked(peer);
                }
            });
        } else {
            holder.rlGeneral.setOnClickListener(null);
        }

        resizeLayout(position, holder.parentSurfaceView);
        resizeLayout(position, holder.avatarLayout);


        MegaChatSession session = ((ChatCallActivity) context).getSessionCall(peer.getPeerId(), peer.getClientId());
        if (peer.isVideoOn() && !call.isOnHold() && (session == null || !session.isOnHold())) {
            /*Hide the avatar and show the video*/
            activateVideo(position, holder, peer);
            checkParticipantAudio(position, holder, peer);

        } else {
            /*Hide the video and show the avatar*/
            deactivateVideo(position, holder, peer);
        }

        checkParticipantQuality(position, holder, peer);

        checkPeerSelected(position, holder, peer);
    }

    /**
     * Method for distributing the screen to the participants.
     *
     * @param position Position of the participant in the adapter.
     * @param holder   The GroupCallAdapter.ViewHolderGroupCall in this position.
     * @param peer     Participant participant to be placed.
     */
    private void refreshRecycler(int position, ViewHolderGroupCall holder, final InfoPeerGroupCall peer) {
        holder = callOrHolderNull(position, holder);
        if(holder == null)
            return;

        int height;
        int width;
        int numPeersOnCall = peers.size();

        if (numPeersOnCall < CHECK_PARTICIPANTS_UI) {
            height = maxScreenHeight / numPeersOnCall;
            width = maxScreenWidth;
        } else if (numPeersOnCall <= MAX_PARTICIPANTS_GRID) {
            height = maxScreenWidth / 2;
            width = numPeersOnCall == CHECK_PARTICIPANTS_UI && position == numPeersOnCall - 1 ? maxScreenWidth : maxScreenWidth / 2;
        } else {
            height = dp2px(SIZE_VIDEO_PARTICIPANTS, outMetrics);
            width = dp2px(SIZE_VIDEO_PARTICIPANTS, outMetrics);
        }

        ViewGroup.LayoutParams lp = holder.rlGeneral.getLayoutParams();
        lp.height = height;
        lp.width = width;
        holder.rlGeneral.setLayoutParams(lp);
        if (numPeersOnCall == CHECK_PARTICIPANTS_UI && position == numPeersOnCall - 1) {
            displayMuteIcon(position, holder, peer);
        }
    }

    /**
     * Distribution of participants depending on the number of participants in the call.
     *
     * @param position Position of the participant in the adapter.
     * @param layout   Layout to be resized.
     */
    private void resizeLayout(int position, final RelativeLayout layout) {
        int numPeersOnCall = peers.size();
        if (numPeersOnCall > MAX_PARTICIPANTS_GRID)
            return;

        /*Distribution of participants depending on the number of participants in the call*/
        int width;
        int height;

        if (numPeersOnCall == 1) {
            width = maxScreenWidth;
            height = maxScreenWidth;
        } else if (numPeersOnCall == 2) {
            width = maxScreenWidth;
            height = maxScreenHeight / numPeersOnCall;
        } else {
            width = maxScreenWidth / 2;
            height = maxScreenWidth / 2;
        }

        RelativeLayout.LayoutParams layoutParamsSurface = (RelativeLayout.LayoutParams) layout.getLayoutParams();
        layoutParamsSurface.width = width;
        layoutParamsSurface.height = height;
        layoutParamsSurface.addRule(RelativeLayout.CENTER_HORIZONTAL, TRUE);
        if (numPeersOnCall == 1 || numPeersOnCall == 2) {
            layoutParamsSurface.addRule(RelativeLayout.CENTER_IN_PARENT, TRUE);

        }
        if (numPeersOnCall == CHECK_PARTICIPANTS_UI || numPeersOnCall == 4) {
            if ((position < 2)) {
                layoutParamsSurface.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, TRUE);
            } else {
                layoutParamsSurface.addRule(RelativeLayout.ALIGN_PARENT_TOP, TRUE);
            }

        } else {
            layoutParamsSurface.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
            layoutParamsSurface.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0);
        }

        layout.setLayoutParams(layoutParamsSurface);
    }

    /**
     * Method for activating a participant's video.
     *
     * @param position Position of the participant in the adapter.
     * @param holder   The GroupCallAdapter.ViewHolderGroupCall in this position.
     * @param peer     Participant who is going to activate the video.
     */
    private void activateVideo(int position, ViewHolderGroupCall holder, final InfoPeerGroupCall peer) {
        holder = callOrHolderNull(position, holder);
        if(holder == null)
            return;

        /*Avatar*/
        holder.avatarLayout.setVisibility(View.GONE);
        holder.muteIconLayout.setVisibility(View.GONE);

        /*Video*/
        if (peer.getListener() == null) {
            holder.parentSurfaceView.removeAllViews();
            TextureView myTexture = new TextureView(context);
            myTexture.setLayoutParams(new RelativeLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
            myTexture.setAlpha(1.0f);
            myTexture.setRotation(0);
            GroupCallListener listenerPeer = new GroupCallListener(myTexture, peer.getPeerId(), peer.getClientId(), chatId, peers.size());
            peer.setListener(listenerPeer);

            if (isItMe(chatId, peer.getPeerId(), peer.getClientId())) {
                megaChatApi.addChatLocalVideoListener(chatId, peer.getListener());
            } else {
                megaChatApi.addChatRemoteVideoListener(chatId, peer.getPeerId(), peer.getClientId(), peer.getListener());
            }

            if (peers.size() <= MAX_PARTICIPANTS_GRID) {
                peer.getListener().getLocalRenderer().addListener(null);
            } else {
                peer.getListener().getLocalRenderer().addListener(this);
            }

            holder.parentSurfaceView.addView(peer.getListener().getSurfaceView());

        } else {
            if (holder.parentSurfaceView.getChildCount() > 0 && !holder.parentSurfaceView.getChildAt(0).equals(peer.getListener().getSurfaceView())) {
                holder.parentSurfaceView.removeAllViews();
                if (peer.getListener().getSurfaceView().getParent() != null && peer.getListener().getSurfaceView().getParent().getParent() != null) {
                    ((ViewGroup) peer.getListener().getSurfaceView().getParent()).removeView(peer.getListener().getSurfaceView());
                }
                holder.parentSurfaceView.addView(peer.getListener().getSurfaceView());
            } else if (holder.parentSurfaceView.getChildCount() == 0) {
                if (peer.getListener().getSurfaceView().getParent() != null && peer.getListener().getSurfaceView().getParent().getParent() != null) {
                    ((ViewGroup) peer.getListener().getSurfaceView().getParent()).removeView(peer.getListener().getSurfaceView());
                }
                holder.parentSurfaceView.addView(peer.getListener().getSurfaceView());
            }

            if (peer.getListener().getHeight() != 0) {
                peer.getListener().setHeight(0);
            }
            if (peer.getListener().getWidth() != 0) {
                peer.getListener().setWidth(0);
            }
        }

        holder.videoLayout.setVisibility(View.VISIBLE);
    }

    /**
     * Method to place the avatars on the screen.
     *
     * @param position Position of the participant in the adapter.
     * @param holder   The GroupCallAdapter.ViewHolderGroupCall in this position.
     * @param peer     Participant's avatar which is going to place the video.
     */
    private void displayAvatar(int position, ViewHolderGroupCall holder, final InfoPeerGroupCall peer) {
        holder = callOrHolderNull(position, holder);
        if(holder == null)
            return;

        int numPeersOnCall = peers.size();
        int size = dp2px(numPeersOnCall <= MAX_PARTICIPANTS_GRID ? SIZE_BIG_AVATAR : SIZE_SMALL_AVATAR, outMetrics);

        if (numPeersOnCall == 2 && isItMe(chatId, peer.getPeerId(), peer.getClientId())) {

            RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) holder.avatarBackground.getLayoutParams();
            layoutParams.width = size;
            layoutParams.height = size;
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, TRUE);
            layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT, 0);
            layoutParams.setMargins(0, 0, 0, scaleHeightPx(MARGIN_BUTTONS_SMALL, outMetrics));
            holder.avatarBackground.setLayoutParams(layoutParams);
            holder.avatarBackground.setGravity(RelativeLayout.ALIGN_PARENT_BOTTOM);
            return;
        }

        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) holder.avatarBackground.getLayoutParams();
        layoutParams.width = size;
        layoutParams.height = size;
        layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT, TRUE);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
        layoutParams.setMargins(0, 0, 0, 0);
        holder.avatarBackground.setLayoutParams(layoutParams);
        holder.avatarBackground.setGravity(RelativeLayout.CENTER_IN_PARENT);
    }

    /**
     * Method to show the call on hold image.
     */
    private void showOnHoldImage(final RelativeLayout avatarLayout, final ImageView avatarImage, final ImageView avatarImageCallOnHold, final InfoPeerGroupCall peer) {
        if (avatarLayout == null)
            return;

        avatarLayout.setVisibility(View.VISIBLE);
        MegaChatCall call = ((ChatCallActivity) context).getCall();
        MegaChatSession session = ((ChatCallActivity) context).getSessionCall(peer.getPeerId(), peer.getClientId());
        if(call == null)
            return;

        boolean shouldBeShown = (call.isOnHold() && isItMe(chatId, peer.getPeerId(), peer.getClientId()))
                || (session != null && session.isOnHold());

        if (shouldBeShown) {
            avatarImageCallOnHold.setImageResource(R.drawable.ic_call_hold_medium);
            avatarImageCallOnHold.setVisibility(View.VISIBLE);
        } else {
            avatarImageCallOnHold.setVisibility(View.GONE);
        }
        avatarImage.setAlpha(call.isOnHold() || (session != null && session.isOnHold()) ? 0.5f : 1f);
    }

    /**
     * Method for deactivating a participant's video.
     *
     * @param position Position of the participant in the adapter.
     * @param holder   The GroupCallAdapter.ViewHolderGroupCall in this position.
     * @param peer     Participant who is going to deactivate the video.
     */
    private void deactivateVideo(int position, ViewHolderGroupCall holder, final InfoPeerGroupCall peer) {
        holder = callOrHolderNull(position, holder);
        if(holder == null)
            return;

        /*Avatar*/
        Bitmap bitmap = getImageAvatarCall(chatRoom, peer.getPeerId());
        if(bitmap != null){
            holder.avatarImage.setImageBitmap(bitmap);
            holder.avatarImage.setCornerRadius(dp2px(getRadius(bitmap), outMetrics));
        }else{
            Bitmap defaultBitmap = getDefaultAvatarCall(context, chatRoom, peer.getPeerId());
            holder.avatarImage.setImageBitmap(defaultBitmap);
            holder.avatarImage.setCornerRadius(dp2px(getRadius(defaultBitmap), outMetrics));
        }

        displayAvatar(position, holder, peer);
        showOnHoldImage(holder.avatarLayout, holder.avatarImage, holder.avatarImageCallOnHold, peer);

        /*Video*/
        holder.videoLayout.setVisibility(View.GONE);
        if (peer.getListener() != null) {
            if (isItMe(chatId, peer.getPeerId(), peer.getClientId())) {
                megaChatApi.removeChatVideoListener(chatId, MEGACHAT_INVALID_HANDLE, MEGACHAT_INVALID_HANDLE, peer.getListener());
            } else {
                megaChatApi.removeChatVideoListener(chatId, peer.getPeerId(), peer.getClientId(), peer.getListener());
            }

            if (holder.parentSurfaceView.getChildCount() > 0) {
                holder.parentSurfaceView.removeAllViews();
            }

            if (peer.getListener().getSurfaceView().getParent() != null && peer.getListener().getSurfaceView().getParent().getParent() != null) {
                ((ViewGroup) peer.getListener().getSurfaceView().getParent()).removeView(peer.getListener().getSurfaceView());
            }

            peer.setListener(null);
        }

        checkParticipantAudio(position, holder, peer);
    }

    /**
     * Method for controlling the participant audio.
     *
     * @param position Position of the participant in the adapter.
     * @param holder   The GroupCallAdapter.ViewHolderGroupCall in this position.
     * @param peer     Participant with changes in the audio.
     */
    private void checkParticipantAudio(int position, ViewHolderGroupCall holder, final InfoPeerGroupCall peer) {
        holder = callOrHolderNull(position, holder);
        if(holder == null)
            return;

        if (!isEstablishedCall(chatId) || peer.isAudioOn() || ((ChatCallActivity) context).getCall().isOnHold()) {
            holder.muteIconLayout.setVisibility(View.GONE);
            return;
        }

        MegaChatSession session = ((ChatCallActivity) context).getSessionCall(peer.getPeerId(), peer.getClientId());
        if (session != null && (session.isOnHold() || session.hasAudio())) {
            holder.muteIconLayout.setVisibility(View.GONE);
            return;
        }

        holder.muteIconLayout.setVisibility(View.VISIBLE);
        displayMuteIcon(position, holder, peer);
    }

    /**
     * Method for controlling the participant quality.
     *
     * @param position Position of the participant in the adapter.
     * @param holder   The GroupCallAdapter.ViewHolderGroupCall in this position.
     * @param peer     Participant with changes in the quality.
     */
    private void checkParticipantQuality(int position, ViewHolderGroupCall holder, final InfoPeerGroupCall peer) {
        holder = callOrHolderNull(position, holder);
        if(holder == null)
            return;

        if (!isEstablishedCall(chatId) || ((ChatCallActivity) context).getCall().isOnHold() || !peer.isVideoOn() || peer.isGoodQuality() || peer.getListener() == null) {
            holder.qualityIcon.setVisibility(View.GONE);
            holder.qualityBackground.setImageBitmap(null);
            holder.qualityBackground.setVisibility(View.GONE);
            return;
        }

        int width;
        int height;
        if (peers.size() == 1) {
            width = maxScreenWidth;
            height = maxScreenWidth;
        } else if (peers.size() == 2) {
            width = maxScreenHeight / 2;
            height = maxScreenHeight / 2;
        } else {
            width = maxScreenWidth / 2;
            height = maxScreenWidth / 2;
        }

        RelativeLayout.LayoutParams paramsQuality = new RelativeLayout.LayoutParams(holder.qualityIcon.getLayoutParams());
        int size;
        int margin;
        if (peers.size() <= MAX_PARTICIPANTS_GRID) {
            size = dp2px(24, outMetrics);
            margin = dp2px(15, outMetrics);
        } else {
            size = dp2px(20, outMetrics);
            margin = dp2px(7, outMetrics);
        }

        paramsQuality.height = size;
        paramsQuality.width = size;
        paramsQuality.setMargins(margin, 0, 0, margin);
        paramsQuality.addRule(RelativeLayout.ALIGN_BOTTOM, holder.parentSurfaceView.getId());
        paramsQuality.addRule(RelativeLayout.ALIGN_LEFT, holder.parentSurfaceView.getId());
        holder.qualityIcon.setLayoutParams(paramsQuality);
        holder.qualityIcon.setVisibility(View.VISIBLE);

        Bitmap bitmap = peer.getListener().getLastFrame(width, height);
        if (bitmap != null) {
            RelativeLayout.LayoutParams paramsQualityLayout = new RelativeLayout.LayoutParams(holder.qualityBackground.getLayoutParams());
            paramsQualityLayout.height = height;
            paramsQualityLayout.width = width;
            paramsQualityLayout.addRule(RelativeLayout.ALIGN_TOP, holder.parentSurfaceView.getId());
            paramsQualityLayout.addRule(RelativeLayout.ALIGN_LEFT, holder.parentSurfaceView.getId());
            holder.qualityBackground.setLayoutParams(paramsQualityLayout);
            Blurry.with(context).from(bitmap).into(holder.qualityBackground);
            holder.qualityBackground.setVisibility(View.VISIBLE);
        } else {
            holder.qualityBackground.setImageBitmap(null);
            holder.qualityBackground.setVisibility(View.GONE);
        }
    }

    /**
     * Method for selecting or deselecting a participant.
     *
     * @param position Position of the participant in the adapter.
     * @param holder   The GroupCallAdapter.ViewHolderGroupCall in this position.
     * @param peer     Participant to be selected or deselected.
     */
    private void checkPeerSelected(int position, ViewHolderGroupCall holder, final InfoPeerGroupCall peer) {
        holder = callOrHolderNull(position, holder);
        if(holder == null)
            return;

        if (peers.size() <= MAX_PARTICIPANTS_GRID) {
            holder.greenLayer.setVisibility(View.GONE);
            peer.setGreenLayer(false);
        } else if (peer.hasGreenLayer()) {
            holder.greenLayer.setBackground(ContextCompat.getDrawable(context, isManualMode ? R.drawable.border_green_layer_selected : R.drawable.border_green_layer));
            holder.greenLayer.setVisibility(View.VISIBLE);
        } else {
            holder.greenLayer.setVisibility(View.GONE);
        }
    }

    /**
     * Method for updating an avatar image.
     *
     * @param email User Email.
     */
    public void updateAvatarImage(String email) {
        if (chatRoom == null)
            return;

        for (InfoPeerGroupCall peer : peers) {
            if (peer.getEmail().equals(email)) {
                Bitmap avatar = getImageAvatarCall(chatRoom, peer.getPeerId());
                if(avatar != null){
                    int position = peers.indexOf(peer);
                    ViewHolderGroupCall holder = getHolder(position);
                    if (holder != null) {
                        holder.avatarImage.setImageBitmap(avatar);
                        holder.avatarImage.setCornerRadius(dp2px(getRadius(avatar), outMetrics));
                    } else {
                        notifyItemChanged(position);
                    }
                }
                break;
            }
        }
    }

    /**
     * Method to updating the mute icon in the video.
     *
     * @param position Position of the participant in the adapter.
     * @param holder   The GroupCallAdapter.ViewHolderGroupCall in this position.
     * @param peer     Participant in which the icon is to be displayed.
     */
    private void displayMuteIcon(int position, ViewHolderGroupCall holder, final InfoPeerGroupCall peer) {
        holder = callOrHolderNull(position, holder);
        if(holder == null)
            return;

        boolean smallIcon = !(peers.size() <= MAX_PARTICIPANTS_GRID);
        int iconRightMargin = dp2px(smallIcon ? MARGIN_MUTE_ICON_SMALL : MARGIN_MUTE_ICON_LARGE, outMetrics);
        int iconTopMargin = dp2px(smallIcon ? MARGIN_MUTE_ICON_SMALL : MARGIN_MUTE_ICON_LARGE, outMetrics);

        if (!smallIcon && ((ChatCallActivity) context).isActionBarShowing() && peers.size() == 2 && position == 0) {
            iconTopMargin += getActionBarHeight(context);
        }

        RelativeLayout.LayoutParams paramsImage = new RelativeLayout.LayoutParams(holder.muteIcon.getLayoutParams());
        paramsImage.height = dp2px(SIZE_MUTE_ICON_LARGE, outMetrics);
        paramsImage.width = dp2px(SIZE_MUTE_ICON_LARGE, outMetrics);
        holder.muteIcon.setLayoutParams(paramsImage);

        RelativeLayout.LayoutParams paramsMicroSurface = new RelativeLayout.LayoutParams(holder.muteIconLayout.getLayoutParams());
        if (peer.isVideoOn()) {
            paramsMicroSurface.addRule(RelativeLayout.ALIGN_RIGHT, holder.videoLayout.getId());
            paramsMicroSurface.addRule(RelativeLayout.ALIGN_TOP, holder.videoLayout.getId());
        } else {
            paramsMicroSurface.addRule(RelativeLayout.ALIGN_RIGHT, holder.avatarLayout.getId());
            paramsMicroSurface.addRule(RelativeLayout.ALIGN_TOP, holder.avatarLayout.getId());
        }
        paramsMicroSurface.setMargins(0, iconTopMargin, iconRightMargin, 0);
        holder.muteIconLayout.setLayoutParams(paramsMicroSurface);
    }

    /**
     * Method for checking whether the mute icon needs to be repositioned.
     */
    public void updateMuteIcon() {
        for (InfoPeerGroupCall peer : peers) {
            int position = peers.indexOf(peer);
            if(!peer.isAudioOn()){
                ViewHolderGroupCall holder = getHolder(position);
                if(holder != null){
                    displayMuteIcon(position, holder, peer);
                }else{
                    notifyItemChanged(position);
                }
            }
        }
    }

    /**
     * Method for updating the UI when the call is put or removed from state on hold.
     */
    public void updateCallOnHold() {
        MegaChatCall call = ((ChatCallActivity) context).getCall();
        for (InfoPeerGroupCall peer : peers) {
            int position = peers.indexOf(peer);
            ViewHolderGroupCall holder = getHolder(position);
            if (holder != null && call.isOnHold()) {
                if (peer.isVideoOn()) {
                    deactivateVideo(position, holder, peer);
                } else {
                    displayAvatar(position, holder, peer);
                    showOnHoldImage(holder.avatarLayout, holder.avatarImage, holder.avatarImageCallOnHold, peer);
                }
                checkParticipantAudio(position, holder, peer);
            } else {
                notifyItemChanged(position);
            }
        }
    }

    /**
     * Method for updating the UI when number of participants changes.
     */
    public void updateAvatarsPosition() {
        for (InfoPeerGroupCall peer : peers) {
            int position = peers.indexOf(peer);
            ViewHolderGroupCall holder = getHolder(position);
            if (holder != null) {
                if (!peer.isVideoOn()) {
                    displayAvatar(position, holder, peer);
                }
            } else {
                notifyItemChanged(position);
            }
        }
    }

    /**
     * Method for updating the UI when a participant put or removed the session state on hold.
     */
    public void updateSessionOnHold(long peerId, long clientId) {
        MegaChatCall call = ((ChatCallActivity) context).getCall();
        for (InfoPeerGroupCall peer : peers) {
            if (peer.getPeerId() == peerId && peer.getClientId() == clientId) {
                int position = peers.indexOf(peer);
                ViewHolderGroupCall holder = getHolder(position);
                if (holder != null && call.isOnHold()) {
                    if (peer.isVideoOn()) {
                        deactivateVideo(position, holder, peer);
                    } else {
                        displayAvatar(position, holder, peer);
                        showOnHoldImage(holder.avatarLayout, holder.avatarImage, holder.avatarImageCallOnHold, peer);
                    }
                } else {
                    notifyItemChanged(position);
                }
            }
        }
    }

    /**
     * Method for updating the UI when the audio of a paticipant changes.
     *
     * @param position The participant position in adapter.
     */
    public void updateParticipantAudio(int position) {
        InfoPeerGroupCall peer = getNodeAt(position);
        if (peer == null)
            return;

        ViewHolderGroupCall holder = getHolder(position);
        if (holder != null) {
            checkParticipantAudio(position, holder, peer);
        } else {
            notifyItemChanged(position);
        }
    }

    /**
     * Method for updating the UI when the selected participant changes.
     *
     * @param position The participant position in adapter.
     */
    public void updatePeerSelected(int position) {
        InfoPeerGroupCall peer = getNodeAt(position);
        if (peer == null)
            return;

        ViewHolderGroupCall holder = getHolder(position);
        if (holder != null) {
            checkPeerSelected(position, holder, peer);
        } else {
            notifyItemChanged(position);
        }
    }

    /**
     * Method for updating the UI when the quality call of a paticipant changes.
     *
     * @param position The participant position in adapter.
     */
    public void updateParticipantQuality(int position) {
        InfoPeerGroupCall peer = getNodeAt(position);
        if (peer == null)
            return;

        ViewHolderGroupCall holder = getHolder(position);
        if (holder != null) {
            checkParticipantQuality(position, holder, peer);
        } else {
            notifyItemChanged(position);
        }
    }

    /**
     * Method to update the manual mode for calls with more than 7 participants.
     *
     * @param flag True, if it needs to be activated. False, if it needs to be deactivated.
     */
    public void updateMode(boolean flag) {
        isManualMode = flag;
    }

    /**
     * Resets the parameters of the participant video.
     *
     * @param peerid   Participant peer ID.
     * @param clientid Participant client ID.
     */
    @Override
    public void resetSize(long peerid, long clientid) {
        logDebug("Peer ID: " + peerid + ", Client ID: " + clientid);
        if (getItemCount() != 0 && peers != null && !peers.isEmpty()) {
            for (InfoPeerGroupCall peer : peers) {
                if (peer.getListener() != null) {
                    if (peer.getListener().getWidth() != 0) {
                        peer.getListener().setWidth(0);
                    }
                    if (peer.getListener().getHeight() != 0) {
                        peer.getListener().setHeight(0);
                    }
                }
            }
        }
    }

    /**
     * Method to destroy the videos of the participants.
     */
    public void onDestroy() {
        ViewHolderGroupCall holder;
        if (peers == null || peers.isEmpty())
            return;

        for (InfoPeerGroupCall peer : peers) {
            int position = peers.indexOf(peer);
            holder = getHolder(position);
            if (holder != null && peer.getListener() != null) {
                if (isItMe(chatId, peer.getPeerId(), peer.getClientId())) {
                    megaChatApi.removeChatVideoListener(chatId, MEGACHAT_INVALID_HANDLE, MEGACHAT_INVALID_HANDLE, peer.getListener());
                } else {
                    megaChatApi.removeChatVideoListener(chatId, peer.getPeerId(), peer.getClientId(), peer.getListener());
                }
                if (holder.parentSurfaceView.getChildCount() != 0) {
                    holder.parentSurfaceView.removeAllViews();
                    holder.parentSurfaceView.removeAllViewsInLayout();
                }

                if (peer.getListener().getSurfaceView().getParent() != null && peer.getListener().getSurfaceView().getParent().getParent() != null) {
                    ((ViewGroup) peer.getListener().getSurfaceView().getParent()).removeView(peer.getListener().getSurfaceView());
                }

                peer.getListener().getSurfaceView().setVisibility(View.GONE);
                peer.setListener(null);

            }
        }
    }

    public ArrayList<InfoPeerGroupCall> getPeers() {
        return peers;
    }

    public void setPeers(ArrayList<InfoPeerGroupCall> peers) {
        this.peers = peers;
    }

    public Object getItem(int position) {
        if (peers != null) {
            return peers.get(position);
        }
        return null;
    }

    public InfoPeerGroupCall getNodeAt(int position) {
        try {
            if (peers != null) {
                return peers.get(position);
            }
        } catch (IndexOutOfBoundsException e) {
            logError("Error. Index out of Bounds");
        }
        return null;
    }

    public RecyclerView getListFragment() {
        return recyclerViewFragment;
    }

    public void setListFragment(RecyclerView recyclerViewFragment) {
        this.recyclerViewFragment = recyclerViewFragment;
    }

    @Override
    public int getItemCount() {
        if (peers != null) {
            return peers.size();
        } else {
            return 0;
        }
    }

    public class ViewHolderGroupCall extends RecyclerView.ViewHolder {
        RelativeLayout rlGeneral;
        RelativeLayout greenLayer;
        RelativeLayout avatarLayout;
        RelativeLayout avatarBackground;
        RoundedImageView avatarImage;
        ImageView avatarImageCallOnHold;
        RelativeLayout muteIconLayout;
        ImageView muteIcon;
        ImageView qualityBackground;
        ImageView qualityIcon;
        RelativeLayout parentSurfaceView;
        RelativeLayout videoLayout;
        long peerId;

        public ViewHolderGroupCall(View itemView) {
            super(itemView);
        }
    }

    public class ViewHolderGroupCallGrid extends ViewHolderGroupCall {
        public ViewHolderGroupCallGrid(View v) {
            super(v);
        }
    }
}