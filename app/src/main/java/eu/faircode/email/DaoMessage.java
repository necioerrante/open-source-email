package eu.faircode.email;

/*
    This file is part of FairEmail.

    FairEmail is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    NetGuard is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with NetGuard.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2018 by Marcel Bokhorst (M66B)
*/

import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.paging.DataSource;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

@Dao
public interface DaoMessage {

    // About 'dummy': "When the min() or max() aggregate functions are used in an aggregate query,
    // all bare columns in the result set take values from the input row which also contains the minimum or maximum."
    // https://www.sqlite.org/lang_select.html

    @Query("SELECT message.*, account.name AS accountName, folder.name as folderName, folder.type as folderType" +
            ", COUNT(message.id) as count" +
            ", SUM(CASE WHEN message.ui_seen THEN 0 ELSE 1 END) as unseen" +
            ", (SELECT COUNT(a.id) FROM attachment a WHERE a.message = message.id) AS attachments" +
            ", MAX(CASE WHEN folder.unified THEN message.id ELSE 0 END) as dummy" +
            " FROM message" +
            " JOIN account ON account.id = message.account" +
            " JOIN folder ON folder.id = message.folder" +
            " WHERE account.`synchronize`" +
            " AND (NOT message.ui_hide OR :debug)" +
            " GROUP BY CASE WHEN message.thread IS NULL THEN message.id ELSE message.thread END" +
            " HAVING SUM(unified) > 0" +
            " ORDER BY message.received DESC")
    DataSource.Factory<Integer, TupleMessageEx> pagedUnifiedInbox(boolean debug);

    @Query("SELECT message.*, account.name AS accountName, folder.name as folderName, folder.type as folderType" +
            ", COUNT(message.id) as count" +
            ", SUM(CASE WHEN message.ui_seen THEN 0 ELSE 1 END) as unseen" +
            ", (SELECT COUNT(a.id) FROM attachment a WHERE a.message = message.id) AS attachments" +
            ", MAX(CASE WHEN folder.id = :folder THEN message.id ELSE 0 END) as dummy" +
            " FROM message" +
            " LEFT JOIN account ON account.id = message.account" +
            " JOIN folder ON folder.id = message.folder" +
            " LEFT JOIN folder f ON f.id = :folder" +
            " WHERE (NOT message.ui_hide OR :debug)" +
            " AND (NOT :found OR ui_found = :found)" +
            " GROUP BY CASE WHEN message.thread IS NULL THEN message.id ELSE message.thread END" +
            " HAVING SUM(CASE WHEN folder.id = :folder THEN 1 ELSE 0 END) > 0" +
            " ORDER BY message.received DESC, message.sent DESC")
    DataSource.Factory<Integer, TupleMessageEx> pagedFolder(long folder, boolean found, boolean debug);

    @Query("SELECT message.*, account.name AS accountName, folder.name as folderName, folder.type as folderType" +
            ", 1 AS count" +
            ", CASE WHEN message.ui_seen THEN 0 ELSE 1 END AS unseen" +
            ", (SELECT COUNT(a.id) FROM attachment a WHERE a.message = message.id) AS attachments" +
            " FROM message" +
            " LEFT JOIN account ON account.id = message.account" +
            " JOIN folder ON folder.id = message.folder" +
            " WHERE (NOT message.ui_hide OR :debug)" +
            " AND message.account = (SELECT m1.account FROM message m1 WHERE m1.id = :msgid)" +
            " AND message.thread = (SELECT m2.thread FROM message m2 WHERE m2.id = :msgid)" +
            " ORDER BY message.received DESC, message.sent DESC")
    DataSource.Factory<Integer, TupleMessageEx> pagedThread(long msgid, boolean debug);

    @Query("SELECT *" +
            " FROM message" +
            " WHERE id = :id")
    EntityMessage getMessage(long id);

    @Query("SELECT COUNT(id)" +
            " FROM message" +
            " WHERE id = :id")
    int countMessage(long id);

    @Query("SELECT *" +
            " FROM message" +
            " WHERE folder = :folder" +
            " AND uid = :uid")
    EntityMessage getMessageByUid(long folder, long uid);

    @Query("SELECT message.* FROM message" +
            " JOIN folder ON folder.id = message.folder" +
            " WHERE message.account = :account" +
            " AND message.msgid = :msgid")
    List<EntityMessage> getMessageByMsgId(long account, String msgid);

    @Query("SELECT message.* FROM message" +
            " JOIN folder ON folder.id = message.folder" +
            " WHERE message.account = :account" +
            " AND message.thread = :thread" +
            " AND folder.type <> '" + EntityFolder.OUTBOX + "'")
    List<EntityMessage> getMessageByThread(long account, String thread);

    @Query("SELECT id FROM message" +
            " WHERE folder = :folder" +
            " ORDER BY message.received DESC, message.sent DESC")
    List<Long> getMessageIDs(long folder);

    @Query("SELECT message.*, account.name AS accountName, folder.name as folderName, folder.type as folderType" +
            ", (SELECT COUNT(m1.id) FROM message m1 WHERE m1.account = message.account AND m1.thread = message.thread AND NOT m1.ui_hide) AS count" +
            ", (SELECT COUNT(m2.id) FROM message m2 WHERE m2.account = message.account AND m2.thread = message.thread AND NOT m2.ui_hide AND NOT m2.ui_seen) AS unseen" +
            ", (SELECT COUNT(a.id) FROM attachment a WHERE a.message = message.id) AS attachments" +
            " FROM message" +
            " LEFT JOIN account ON account.id = message.account" +
            " JOIN folder ON folder.id = message.folder" +
            " WHERE message.id = :id")
    LiveData<TupleMessageEx> liveMessage(long id);

    @Query("SELECT message.* FROM message" +
            " JOIN account ON account.id = message.account" +
            " JOIN folder ON folder.id = message.folder" +
            " WHERE account.`synchronize`" +
            " AND folder.unified" +
            " AND NOT message.ui_seen AND NOT message.ui_hide" +
            " AND (account.seen_until IS NULL OR message.stored > account.seen_until)" +
            " ORDER BY message.received")
    LiveData<List<EntityMessage>> liveUnseenUnified();

    @Query("SELECT uid FROM message" +
            " WHERE folder = :folder" +
            " AND received >= :received" +
            " AND NOT uid IS NULL" +
            " AND NOT ui_found" /* keep found messages */)
    List<Long> getUids(long folder, long received);

    @Insert
    long insertMessage(EntityMessage message);

    @Update
    void updateMessage(EntityMessage message);

    @Query("UPDATE message SET uid = :uid WHERE id = :id")
    int setMessageUid(long id, Long uid);

    @Query("UPDATE message SET seen = :seen WHERE id = :id")
    int setMessageSeen(long id, boolean seen);

    @Query("UPDATE message SET ui_seen = :ui_seen WHERE id = :id")
    int setMessageUiSeen(long id, boolean ui_seen);

    @Query("UPDATE message SET ui_hide = :ui_hide WHERE id = :id")
    int setMessageUiHide(long id, boolean ui_hide);

    @Query("UPDATE message SET error = :error WHERE id = :id")
    int setMessageError(long id, String error);

    @Query("UPDATE message SET ui_found = :found WHERE id = :id")
    int setMessageFound(long id, boolean found);

    @Query("UPDATE message SET ui_found = 0 WHERE folder = :folder")
    int resetFound(long folder);

    @Query("UPDATE message SET headers = :headers WHERE id = :id")
    int setMessageHeaders(long id, String headers);

    @Query("DELETE FROM message WHERE id = :id")
    int deleteMessage(long id);

    @Query("DELETE FROM message WHERE folder = :folder AND uid = :uid")
    int deleteMessage(long folder, long uid);

    @Query("DELETE FROM message WHERE folder = :folder")
    int deleteMessages(long folder);

    @Query("DELETE FROM message WHERE folder = :folder AND received < :received AND NOT uid IS NULL")
    int deleteMessagesBefore(long folder, long received);

    @Query("DELETE FROM message WHERE ui_found")
    int deleteFoundMessages();
}