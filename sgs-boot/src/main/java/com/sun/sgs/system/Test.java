package com.sun.sgs.system;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 测试
 */
public class Test {
    private static final ReentrantLock LOCK = new ReentrantLock();  //可重入锁
    private static final Condition STOP = LOCK.newCondition();

    public static void main(String[] args) {
    Thread t;

    System.out.println(Thread.currentThread().getName()+" "+Thread.currentThread().getId());
//        AbstractApplicationContext applicationContext = new ClassPathXmlApplicationContext("applicationContext.xml");
//        applicationContext.start();
//        logger.info("service start success !~");
        System.out.println("start");
        addHook();
        //主线程阻塞等待，守护线程释放锁后退出
        try {
            System.out.println("start1212");
            LOCK.lock();
            STOP.await();
        } catch (InterruptedException e) {
            System.out.println(" service   stopped, interrupted by other thread!");
        } finally {
            LOCK.unlock();
        }
        System.out.println("end");
    }

    /**
     * Created on 2017年12月12日
     * <p>
     * Discription:[添加一个守护线程]
     *
     * @param applicationContext
     *
     */
    private static void addHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {

//            try {
//
////                applicationContext.stop();
//            } catch (Exception e) {
//                System.out.println("StartMain stop exception ");
//            }

                System.out.println("jvm exit, all service stopped.");
                try {
                    LOCK.lock();
                    STOP.signal();
                } finally {
                    LOCK.unlock();
                }
            }
        }, "StartMain-shutdown-hook"));
    }
}
