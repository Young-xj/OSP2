/* Author: Daniel Vu
 * Email Address: vud@email.sc.edu
 * Date Last Modified: September 30, 2014
 * OSP2 RR Scheduling
 *
 * With code from 
 * Introduction to Operating System Design and Implementation: 
 * The OSP 2 Approach 
 * Michael Kifer and Scott Smolka, Springer, 2007
 *
 * The code is attached with numbering that represent the corresponding step in Dr. Rose's outline.
 */

package osp.Threads;

import osp.IFLModules.*;
import osp.Utilities.*;
import osp.Hardware.*;

/**    
       The timer interrupt handler.  This class is called upon to
       handle timer interrupts.

       @OSPProject Threads
*/
public class TimerInterruptHandler extends IflTimerInterruptHandler
{
    /**
       This basically only needs to reset the times and dispatch
       another process.

       @OSPProject Threads
    */
    public void do_handleInterrupt()
    {
        ThreadCB.dispatch();

    }


    /*
       Feel free to add methods/fields to improve the readability of your code
    */

}

/*
      Feel free to add local classes to improve the readability of your code
*/
