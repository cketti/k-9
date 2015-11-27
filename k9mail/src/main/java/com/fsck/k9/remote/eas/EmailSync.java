package com.fsck.k9.remote.eas;


import java.util.ArrayList;
import java.util.List;

import android.content.Context;

import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.data.MessageServerData;
import com.fsck.k9.mail.store.eas.Account;
import com.fsck.k9.mail.store.eas.EasSync;
import com.fsck.k9.mail.store.eas.EasSyncBase;
import com.fsck.k9.mail.store.eas.EasSyncMail;
import com.fsck.k9.mail.store.eas.Mailbox;
import com.fsck.k9.mail.store.eas.MessageStateChange;
import com.fsck.k9.mail.store.eas.callback.EmailSyncCallback;
import com.fsck.k9.remote.BackendStorage;


class EmailSync {
    private final Context context;
    private final Account account;
    private final BackendStorage backendStorage;


    EmailSync(Context context, Account account, BackendStorage backendStorage) {
        this.context = context;
        this.account = account;
        this.backendStorage = backendStorage;
    }

    public boolean syncFolder(String serverId) {
        Mailbox mailbox = createMailbox(serverId);

        EasSyncMail syncMail = new EasSyncMail(new BackendEmailSyncCallback(mailbox));

        EasSyncBase syncBase = new EasSyncBase(context, account, mailbox, syncMail);
        int result = syncBase.performOperation();

        return result >= EasSyncBase.RESULT_MIN_OK_RESULT;
    }

    public boolean setFlag(String folderServerId, List<String> messageServerIds, Flag flag, boolean newState) {
        Mailbox mailbox = createMailbox(folderServerId);
        List<MessageStateChange> changes = createMessageStateChangeList(messageServerIds, flag, newState);
        BackendEmailSyncCallback callback = new BackendEmailSyncCallback(mailbox);

        EasSync easSync = new EasSync(context, account, mailbox, changes, callback);
        int result = easSync.upsync();

        return result == EasSync.RESULT_OK;
    }

    private List<MessageStateChange> createMessageStateChangeList(List<String> messageServerIds, Flag flag,
            boolean newState) {
        List<MessageStateChange> changes = new ArrayList<MessageStateChange>(messageServerIds.size());
        for (String messageServerId : messageServerIds) {
            MessageStateChange change = MessageStateChange.builder()
                    .serverId(messageServerId)
                    .setFlag(flag, newState)
                    .build();

            changes.add(change);
        }

        return changes;
    }

    private Mailbox createMailbox(String serverId) {
        Mailbox mailbox = new Mailbox();
        mailbox.mServerId = serverId;
        mailbox.mSyncKey = backendStorage.getSyncKeyForFolder(serverId);
        return mailbox;
    }

    class BackendEmailSyncCallback implements EmailSyncCallback {
        private final Mailbox mailbox;
        private boolean syncKeyChanged = false;


        BackendEmailSyncCallback(Mailbox mailbox) {
            this.mailbox = mailbox;
        }

        @Override
        public void addMessage(MessageServerData messageServerData) {
            backendStorage.createMessage(messageServerData);
        }

        @Override
        public void removeMessage(String serverId) {
            String folderServerId = mailbox.mServerId;
            backendStorage.removeMessage(folderServerId, serverId);
        }

        @Override
        public void readStateChanged(String serverId, boolean read) {
            String folderServerId = mailbox.mServerId;
            backendStorage.setMessageFlag(folderServerId, serverId, Flag.SEEN, read);
        }

        @Override
        public void flagStateChanged(String serverId, boolean flag) {
            String folderServerId = mailbox.mServerId;
            backendStorage.setMessageFlag(folderServerId, serverId, Flag.FLAGGED, flag);
        }

        @Override
        public void messageWasRepliedTo(String serverId) {
            String folderServerId = mailbox.mServerId;
            backendStorage.setMessageFlag(folderServerId, serverId, Flag.ANSWERED, true);
        }

        @Override
        public void messageWasForwarded(String serverId) {
            String folderServerId = mailbox.mServerId;
            backendStorage.setMessageFlag(folderServerId, serverId, Flag.FORWARDED, true);
        }

        @Override
        public void commitMessageChanges() {
            saveSyncKeyIfChanged();
        }

        private void saveSyncKeyIfChanged() {
            if (syncKeyChanged) {
                backendStorage.setSyncKeyForFolder(mailbox.mServerId, mailbox.mSyncKey);
            }
        }

        @Override
        public boolean isFirstSync() {
            return "0".equals(mailbox.mSyncKey);
        }

        @Override
        public boolean setSyncKey(String syncKey) {
            String oldSyncKey = mailbox.mSyncKey;
            boolean changed = oldSyncKey == null || !oldSyncKey.equals(syncKey);
            if (!syncKeyChanged && changed) {
                syncKeyChanged = true;
            }

            mailbox.mSyncKey = syncKey;

            return changed;
        }

        @Override
        public void restartSync() {
            String folderServerId = mailbox.mServerId;
            backendStorage.removeAllMessages(folderServerId);
        }

        @Override
        public void wipe() {
            //TODO: implement
        }
    }
}
