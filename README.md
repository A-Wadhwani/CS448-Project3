# CS448-Project3 Tests
## 1. Idle Test
1.	Delete the idletest folder
2.	Change the *main()* in **RecoveryTest** to have run only the method *idleTest()*. 
3.	Run **RecoveryTest** once.
4.	Without changing the idletest folder, run **RecoveryTest** once.
## 2. Checkpoint Test
1.	Delete the checkpointtest folder
2.	Change the *main()* in **RecoveryTest** to have run only the method *checkpointTest()*. 
3.	Run **RecoveryTest** once.
4.	Without changing the checkpointtest folder, run **RecoveryTest** once.
## 3. Original Test
1.	Delete the originaltest folder
2.	Change the *main()* in **RecoveryTest** to have run only the method *originalTest()*. 
3.	Run **RecoveryTest** once.
4.	Without changing the originaltest, run **RecoveryTest** once.
## 4. Query Implementation Test
This is the most involved of the test cases, but the steps are very straightforward. Please note that if a mistake occurs while running these instructions, the entire setup needs to be run again, it cannot be run from the previous step.
### Setting Up Database
1.	Delete the studentdb folder.
2.	Run **StartServer**
3.	Run **CreateStudentDB**, so that the Database is created.
4.	Restart **StartServer**. 
### Non-Checkpoint Test
1.	Run **SimpleIJ**, it should automatically be connected to the Server now. 
2.	Run the command `update STUDENT set MajorId=10`
3.	Run the command `exit`
4.	Restart **StartServer**. Keep track of the “Log File Reads” count this time.
### Checkpoint Test
1.	Run **SimpleIJ**, it should automatically be connected to the Server now. 
2.	Run the command `update STUDENT set MajorId=14`
3.	Run the command `checkpoint`
4.	Run the command `exit`
5.	Restart **StartServer**. Keep track of the “Log File Reads” count.
