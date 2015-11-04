package com.fsck.k9.activity;

import android.content.Context;

import com.fsck.k9.Account;
import com.fsck.k9.R;
import com.fsck.k9.mail.Folder;
import com.fsck.k9.mailstore.LocalFolder;


public class FolderInfoHolder implements Comparable<FolderInfoHolder> {
    public String name;
    public String displayName;
    public long lastChecked;
    public int unreadMessageCount = -1;
    public int flaggedMessageCount = -1;
    public boolean loading;
    public String status;
    public boolean lastCheckFailed;
    public Folder folder;
    public boolean pushActive;
    public boolean moreMessages;

    @Override
    public boolean equals(Object o) {
        return o instanceof FolderInfoHolder && name.equals(((FolderInfoHolder) o).name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    public int compareTo(FolderInfoHolder o) {
        String s1 = this.name;
        String s2 = o.name;

        int ret = s1.compareToIgnoreCase(s2);
        if (ret != 0) {
            return ret;
        } else {
            return s1.compareTo(s2);
        }

    }

    private String truncateStatus(String mess) {
        if (mess != null && mess.length() > 27) {
            mess = mess.substring(0, 27);
        }
        return mess;
    }

    // constructor for an empty object for comparisons
    public FolderInfoHolder() {
    }

    public FolderInfoHolder(Context context, LocalFolder folder, Account account) {
        if (context == null) {
            throw new IllegalArgumentException("null context given");
        }
        populate(context, folder, account);
    }

    public FolderInfoHolder(Context context, LocalFolder folder, Account account, int unreadCount) {
        populate(context, folder, account, unreadCount);
    }

    public void populate(Context context, LocalFolder folder, Account account, int unreadCount) {
        populate(context, folder, account);
        this.unreadMessageCount = unreadCount;
        folder.close();

    }


    public void populate(Context context, LocalFolder folder, Account account) {
        this.folder = folder;
        this.name = folder.getName();
        this.lastChecked = folder.getLastUpdate();

        this.status = truncateStatus(folder.getStatus());

        this.displayName = getDisplayName(context, account, name, folder.getDisplayName());
        setMoreMessagesFromFolder(folder);
    }

    /**
     * Returns the display name for a folder.
     *
     * <p>
     * This will append localized strings for special folders like the Inbox or the Trash folder.
     * </p>
     */
    public static String getDisplayName(Context context, Account account, String name, String displayName) {
        final String augmentedDisplayName;
        if (name.equals(account.getSpamFolderName())) {
            augmentedDisplayName = String.format(
                    context.getString(R.string.special_mailbox_name_spam_fmt), displayName);
        } else if (name.equals(account.getArchiveFolderName())) {
            augmentedDisplayName = String.format(
                    context.getString(R.string.special_mailbox_name_archive_fmt), displayName);
        } else if (name.equals(account.getSentFolderName())) {
            augmentedDisplayName = String.format(
                    context.getString(R.string.special_mailbox_name_sent_fmt), displayName);
        } else if (name.equals(account.getTrashFolderName())) {
            augmentedDisplayName = String.format(
                    context.getString(R.string.special_mailbox_name_trash_fmt), displayName);
        } else if (name.equals(account.getDraftsFolderName())) {
            augmentedDisplayName = String.format(
                    context.getString(R.string.special_mailbox_name_drafts_fmt), displayName);
        } else if (name.equals(account.getOutboxFolderName())) {
            augmentedDisplayName = context.getString(R.string.special_mailbox_name_outbox);
        } else if (name.equals(account.getInboxFolderName())) {
            augmentedDisplayName = context.getString(R.string.special_mailbox_name_inbox);
        } else {
            augmentedDisplayName = displayName;
        }

        return augmentedDisplayName;
    }

    public void setMoreMessagesFromFolder(LocalFolder folder) {
        moreMessages = folder.hasMoreMessages();
    }
}
