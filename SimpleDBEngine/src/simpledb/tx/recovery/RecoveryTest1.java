//package simpledb.tx.recovery;
//
//import simpledb.server.SimpleDB;
//import simpledb.file.*;
//import simpledb.buffer.BufferMgr;
//import simpledb.tx.Transaction;
//
//import java.util.Collection;
//
//public class RecoveryTest1 {
//   public static FileMgr fm;
//   public static BufferMgr bm;
//   private static SimpleDB db;
//   private static BlockId blk0, blk1;
//   private static Collection<Integer> active;
//   public static void main(String[] args) throws Exception {
//      long startTime = System.nanoTime();
//      db = new SimpleDB("recoverytest", 400, 8);
//      fm = db.fileMgr();
//      bm = db.bufferMgr();
//      blk0 = new BlockId("testfile", 0);
//      blk1 = new BlockId("testfile", 1);
//
//      if (fm.length("testfile") == 0) {
//         initialize();
//         modify();
//         recover(active);
//         end(active);
//
//      }
//      long endTime = System.nanoTime();
//      long duration = (endTime - startTime);
//      System.out.println("-----------duration: " + (duration * 1.0/1000000 ));
//   }
//
//   private static void initialize() {
//      Transaction tx1 = db.newTx();
//      Transaction tx2 = db.newTx();
//      tx1.pin(blk0);
//      tx2.pin(blk1);
//      int pos = 0;
//      for (int i=0; i<6; i++) {
//         tx1.setInt(blk0, pos, pos, false);
//         tx2.setInt(blk1, pos, pos, false);
//         pos += Integer.BYTES;
//      }
//      tx1.setString(blk0, 30, "abc", false);
//      tx2.setString(blk1, 30, "def", false);
//      tx1.commit();
//      tx2.commit();
//
//   }
//
//   private static void modify() {
//      Transaction tx3 = db.newTx();
//      Transaction tx4 = db.newTx();
//      tx3.pin(blk0);
//      tx4.pin(blk1);
//      tx3.setString(blk0, 30, "uvw", true);
//      tx4.setString(blk1, 30, "xyz", true);
//      bm.flushAll(3);
//      bm.flushAll(4);
//
//      start();
//
//      tx3.rollback();
//      tx4.commit();
//
//   }
//
//
//   private static void recover(Collection<Integer> activeTransactions) {
//      Transaction tx5 = db.newTx();
//      tx5.recover();
//
//   }
//   private static void start(){
//      Transaction tx6 = db.newTx();
//      tx6.checkpoint();
//   }
//   private static void end(Collection<Integer> activeTransactions){
//      Transaction tx7 = db.newTx();
//      tx7.end(activeTransactions);
//   }
//
//   // Print the values that made it to disk.
//   private static void printValues(String msg) {
//      System.out.println(msg);
//      Page p0 = new Page(fm.blockSize());
//      Page p1 = new Page(fm.blockSize());
//      fm.read(blk0, p0);
//      fm.read(blk1, p1);
//      int pos = 0;
//      for (int i=0; i<6; i++) {
//         System.out.print(p0.getInt(pos) + " ");
//         System.out.print(p1.getInt(pos) + " ");
//         pos += Integer.BYTES;
//      }
//      System.out.print(p0.getString(30) + " ");
//      System.out.print(p1.getString(30) + " ");
//      System.out.println();
//   }
//}
