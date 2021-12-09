package simpledb.tx.recovery;

import java.util.*;

import simpledb.file.*;
import simpledb.log.*;
import simpledb.buffer.*;
import simpledb.tx.Transaction;

import static simpledb.tx.recovery.LogRecord.*;

/**
 * The recovery manager.  Each transaction has its own recovery manager.
 *
 * @author Edward Sciore
 */
public class RecoveryMgr {
    private LogMgr lm;
    private BufferMgr bm;
    private Transaction tx;
    private int txnum;
    public static int iterations;
    public static boolean DEBUG_MODE = true;

    /**
     * Create a recovery manager for the specified transaction.
     *
     * @param txnum the ID of the specified transaction
     */
    public RecoveryMgr(Transaction tx, int txnum, LogMgr lm, BufferMgr bm) {
        this.tx = tx;
        this.txnum = txnum;
        this.lm = lm;
        this.bm = bm;
        StartRecord.writeToLog(lm, txnum);
    }

    /**
     * Write a commit record to the log, and flushes it to disk.
     */
    public void commit() {
//        bm.flushAll(txnum);
        int lsn = CommitRecord.writeToLog(lm, txnum);
        lm.flush(lsn);
    }

    /**
     * Write a rollback record to the log and flush it to disk.
     */
    public void rollback() {
        doRollback();
        bm.flushAll(txnum);
        int lsn = RollbackRecord.writeToLog(lm, txnum);
        lm.flush(lsn);
    }

    /**
     * Recover uncompleted transactions from the log
     * and then write a quiescent checkpoint record to the log and flush it.
     */
    public void recover() {
        doRecover();
        if (DEBUG_MODE) {
            System.out.println("Recovery Complete: " + getLog());
            System.out.println();
            System.out.println("Log File Reads: " + iterations);
        }
        bm.flushAll();
        int lsn = CheckpointRecord.writeToLog(lm);
        lm.flush(lsn);
    }

    public void checkpoint() {
        while (!Transaction.isIdle(txnum)) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
        }
        bm.flushAll();
        int lsn = CheckpointRecord.writeToLog(lm);
        lm.flush(lsn);
        if (DEBUG_MODE) {
            System.out.println("Checkpoint Made: " + getLog());
            System.out.println();
        }
    }

    /**
     * Write a setint record to the log and return its lsn.
     *
     * @param buff   the buffer containing the page
     * @param offset the offset of the value in the page
     * @param newval the value to be written
     */
    public int setInt(Buffer buff, int offset, int newval) {
        int oldval = buff.contents().getInt(offset);
        BlockId blk = buff.block();
        return SetIntRecord.writeToLog(lm, txnum, blk, offset, oldval, newval);
    }

    /**
     * Write a setstring record to the log and return its lsn.
     *
     * @param buff   the buffer containing the page
     * @param offset the offset of the value in the page
     * @param newval the value to be written
     */
    public int setString(Buffer buff, int offset, String newval) {
        String oldval = buff.contents().getString(offset);
        BlockId blk = buff.block();
        return SetStringRecord.writeToLog(lm, txnum, blk, offset, oldval, newval);
    }

    /**
     * Rollback the transaction, by iterating
     * through the log records until it finds
     * the transaction's START record,
     * calling undo() for each of the transaction's
     * log records.
     */
    private void doRollback() {
        Iterator<byte[]> iter = lm.iterator();
        while (iter.hasNext()) {
            byte[] bytes = iter.next();
            LogRecord rec = LogRecord.createLogRecord(bytes);
            if (rec.txNumber() == txnum) {
                if (rec.op() == START)
                    return;
                rec.undo(tx);
            }
        }
    }

    /**
     * Do a complete database recovery.
     * The method iterates through the log records.
     * Whenever it finds a log record for an unfinished
     * transaction, it calls undo() on that record.
     * The method stops when it encounters a CHECKPOINT record
     * or the end of the log.
     */
    private void doRecover() {
        //finding transactions after checkpoint
        ArrayList<LogRecord> logList = new ArrayList<>();
        Iterator<byte[]> iter = lm.iterator();
        while (iter.hasNext()) {
            byte[] bytes = iter.next();
            LogRecord rec = LogRecord.createLogRecord(bytes);
            logList.add(rec);
            if (rec.op() == CHECKPOINT) {
                break;
            }
        }
        //redo phase
        Collections.reverse(logList);
        Collection<Integer> undoList = new ArrayList<>();
        for (int i = 0; i < logList.size(); i++) {
            LogRecord record = logList.get(i);
            if (record.op() == START) {
                undoList.add(record.txNumber());
            } else if (record.op() == COMMIT || record.op() == ROLLBACK) {
                undoList.remove(record.txNumber());
            }
            record.redo(tx);
        }
        //undo phase
        iter = lm.iterator();
        while (iter.hasNext()) {
            byte[] bytes = iter.next();
            LogRecord rec = LogRecord.createLogRecord(bytes);
            if (undoList.isEmpty()) {
                break;
            } else if (undoList.contains(rec.txNumber()) && (rec.op() == SETSTRING || rec.op() == SETINT)) {
                rec.undo(tx);
            } else if (undoList.contains(rec.txNumber()) && rec.op() == START) {
                undoList.remove(rec.txNumber());
                int lsn = RollbackRecord.writeToLog(lm, txnum);
                lm.flush(lsn);
            }
        }
    }

    /**
     * getLog(), but until first checkpoint is reached.
     */
    public String getLogTC() {
        Iterator<byte[]> iter = lm.iterator();
        String list = "";
        boolean lastModified = true;
        while (iter.hasNext()) {
            byte[] bytes = iter.next();
            LogRecord rec = LogRecord.createLogRecord(bytes);
            switch (rec.op()) {
                case CHECKPOINT:
                    list += "CHECKPOINT-";
                    return list;
                case START:
                    list += "START-";
                    lastModified = false;
                    break;
                case COMMIT:
                    list += "COMMIT-";
                    lastModified = false;
                    break;
                case ROLLBACK:
                    list += "ROLLBACK-";
                    lastModified = false;
                    break;
                case SETINT:
                case SETSTRING:
                    if (!lastModified) {
                        list += "MODIFY-";
                        lastModified = true;
                    }
                    break;
            }
        }
        return list;
    }

    public String getLog() {
        Iterator<byte[]> iter = lm.iterator();
        String list = "";
        boolean lastModified = true;
        while (iter.hasNext()) {
            byte[] bytes = iter.next();
            LogRecord rec = LogRecord.createLogRecord(bytes);
            switch (rec.op()) {
                case CHECKPOINT:
                    list += "CHECKPOINT-";
                    break;
                case START:
                    list += "START-";
                    lastModified = false;
                    break;
                case COMMIT:
                    list += "COMMIT-";
                    lastModified = false;
                    break;
                case ROLLBACK:
                    list += "ROLLBACK-";
                    lastModified = false;
                    break;
                case SETINT:
                case SETSTRING:
                    if (!lastModified) {
                        list += "MODIFY-";
                        lastModified = true;
                    }
                    break;
            }
        }
        return list;
    }
}
