package simpledb.tx.recovery;

import simpleclient.network.CreateStudentDB;
import simpledb.server.SimpleDB;
import simpledb.file.*;
import simpledb.buffer.BufferMgr;
import simpledb.tx.Transaction;

public class RecoveryTest {
    public static FileMgr fm;
    public static BufferMgr bm;
    private static SimpleDB db;
    private static BlockId blk0, blk1;
    private static Transaction tx4;

    public static void main(String[] args) throws Exception {
//        idleTest();
//        checkpointTest();
//        originalTest();
    }


    /*
     * Remove idletest folder. Run twice.
     * Observe how the checkpoint is only made after
     * other transactions are committed.
     */
    public static void idleTest() {
        db = new SimpleDB("idletest", 400, 8);
        fm = db.fileMgr();
        bm = db.bufferMgr();
        blk0 = new BlockId("testfile", 0);
        blk1 = new BlockId("testfile", 1);

        if (fm.length("testfile") == 0) {
            initialize();
            modify();
            stayidle();
            checkpoint();
        } else {
            recover();
        }
    }

    /*
     * Remove checkpointtest folder. Run twice.
     * In comparison to Original test, a checkpoint
     * is created after the transactions are committed.
     * We should be able to see the changes after
     * rollback visible when running it again.
     */
    public static void checkpointTest() {
        db = new SimpleDB("checkpointtest", 400, 8);
        fm = db.fileMgr();
        bm = db.bufferMgr();
        blk0 = new BlockId("testfile", 0);
        blk1 = new BlockId("testfile", 1);

        if (fm.length("testfile") == 0) {
            initialize();
            modify();
            checkpoint();
            System.out.println(tx4.getLog());
        } else {
            recover();
            System.out.println("Undos with Checkpoint: " + RecoveryMgr.iterations);
        }
    }

    public static void originalTest() {
        db = new SimpleDB("originaltest", 400, 8);
        fm = db.fileMgr();
        bm = db.bufferMgr();
        blk0 = new BlockId("testfile", 0);
        blk1 = new BlockId("testfile", 1);

        if (fm.length("testfile") == 0) {
            initialize();
            modify();
        } else {
            recover();
            System.out.println("Undos without Checkpoint: " + RecoveryMgr.iterations);
        }
    }

    private static void initialize() {
        Transaction tx1 = db.newTx();
        Transaction tx2 = db.newTx();
        tx1.pin(blk0);
        tx2.pin(blk1);
        int pos = 0;
        for (int i = 0; i < 6; i++) {
            tx1.setInt(blk0, pos, pos, false);
            tx2.setInt(blk1, pos, pos, false);
            pos += Integer.BYTES;
        }
        tx1.setString(blk0, 30, "abc", false);
        tx2.setString(blk1, 30, "def", false);
        tx1.commit();
        tx2.commit();
        printValues("After Initialization:");
    }

    private static void modify() {
        Transaction tx3 = db.newTx();
        tx4 = db.newTx();
        tx3.pin(blk0);
        tx4.pin(blk1);
        int pos = 0;
        for (int i = 0; i < 6; i++) {
            tx3.setInt(blk0, pos, pos + 100, true);
            tx4.setInt(blk1, pos, pos + 100, true);
            pos += Integer.BYTES;
        }
        tx3.setString(blk0, 30, "uvw", true);
        tx4.setString(blk1, 30, "xyz", true);
        bm.flushAll(3);
        bm.flushAll(4);
        printValues("After modification:");

        tx3.rollback();
        printValues("After rollback:");
        // tx4 stops here without committing or rolling back,
        // so all its changes should be undone during recovery.
    }

    private static void checkpoint(){
        System.out.println("Attempting Checkpoint");
        tx4.checkpoint(); // Attempt making a checkpoint
        System.out.println("Checkpoint Created");
    }

    private static void stayidle() {
        System.out.println("Beginning Idling");
        // New Transaction
        Runnable task2 = () -> {
            Transaction tx5 = db.newTx();
            tx5.pin(blk0);
            int pos = 0;
            for (int i = 0; i < 6; i++) {
                tx5.setInt(blk0, pos, pos + 100, true);
                pos += Integer.BYTES;
            }
            tx5.setString(blk0, 30, "pqr", true);
            bm.flushAll(5);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {}
            tx5.commit();
            printValues("After idling:");
        };
        new Thread(task2).start();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {}
    }

    private static void recover() {
        Transaction tx = db.newTx();
        tx.recover();
        printValues("After recovery:");
    }

    // Print the values that made it to disk.
    private static void printValues(String msg) {
        System.out.println(msg);
        Page p0 = new Page(fm.blockSize());
        Page p1 = new Page(fm.blockSize());
        fm.read(blk0, p0);
        fm.read(blk1, p1);
        int pos = 0;
        for (int i = 0; i < 6; i++) {
            System.out.print(p0.getInt(pos) + " ");
            System.out.print(p1.getInt(pos) + " ");
            pos += Integer.BYTES;
        }
        System.out.print(p0.getString(30) + " ");
        System.out.print(p1.getString(30) + " ");
        System.out.println();
    }
}
