package net.es.lookup.service;


import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.es.lookup.common.MemoryManager;
import net.es.lookup.common.exception.internal.DatabaseException;
import net.es.lookup.database.MongoDBMaintenanceJob;
import net.es.lookup.database.ServiceDAOMongoDb;
import net.es.lookup.pubsub.client.Cache;
import net.es.lookup.timer.Scheduler;
import net.es.lookup.utils.config.reader.LookupServiceConfigReader;
import net.es.lookup.utils.config.reader.QueueServiceConfigReader;
import net.es.lookup.utils.config.reader.SubscriberConfigReader;
import net.es.lookup.utils.log.StdOutErrLog;
import org.quartz.JobDetail;
import org.quartz.Trigger;

import java.util.LinkedList;
import java.util.List;

import static java.util.Arrays.asList;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;


public class Invoker {

    private static int port = 8080;
    private static LookupService lookupService = null;
    private static CacheService cacheService = null;
    //private static ServiceDAOMongoDb dao = null;
    private static String host = "localhost";
    private static LookupServiceConfigReader lcfg;
    private static SubscriberConfigReader sfg;
    private static QueueServiceConfigReader qcfg;
    private static String configPath = "etc/";
    private static String lookupservicecfg = "lookupservice.yaml";
    private static String queuecfg = "queueservice.yaml";
    private static String subscribecfg = "subscriber.yaml";
    private static String logConfig = "./etc/log4j.properties";
    private static String queueDataDir = "../elements";

    private static String dataDir = "data/";


    private static boolean cacheServiceRequest = false;


    public static String getDataDir() {

        return dataDir;
    }

    /**
     * Main program to start the Lookup ServiceRecord
     *
     * @param args [-h, ?] for help
     *             [-p server-port
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {

        parseArgs(args);
        //set log config
        System.setProperty("log4j.configuration", "file:" + logConfig);
        StdOutErrLog.redirectStdOutErrToLog();

        Scheduler scheduler = Scheduler.getInstance();


        LookupServiceConfigReader.init(configPath + lookupservicecfg);
        QueueServiceConfigReader.init(configPath + queuecfg);


        lcfg = LookupServiceConfigReader.getInstance();
        qcfg = QueueServiceConfigReader.getInstance();

        port = lcfg.getPort();
        host = lcfg.getHost();

        int dbpruneInterval = lcfg.getPruneInterval();
        long prunethreshold = lcfg.getPruneThreshold();
        System.out.println("starting ServiceDAOMongoDb");

        String dburl = lcfg.getDbUrl();
        int dbport = lcfg.getDbPort();
        String collname = lcfg.getCollName();

        List<String> services = new LinkedList<String>();
        List<Cache> cacheList = new LinkedList<Cache>();
        // Initialize services
        try {

            if (lcfg.isCoreserviceOn()) {
                new ServiceDAOMongoDb(dburl, dbport, LookupService.LOOKUP_SERVICE, collname);

                services.add(LookupService.LOOKUP_SERVICE);
            }

        } catch (DatabaseException e) {

            System.out.println("Error connecting to database; Please check if MongoDB is running");
            System.exit(1);

        }
        System.out.println("starting Lookup Service");
        // Create the REST service
        Invoker.lookupService = new LookupService(Invoker.host, Invoker.port);


        // Start the service
        Invoker.lookupService.startService(services);

        //DB Pruning for core LS
        if (lcfg.isCoreserviceOn()) {
            JobDetail job = newJob(MongoDBMaintenanceJob.class)
                    .withIdentity(LookupService.LOOKUP_SERVICE + "clean", "DBMaintenance")
                    .build();
            job.getJobDataMap().put(MongoDBMaintenanceJob.PRUNE_THRESHOLD, prunethreshold);
            job.getJobDataMap().put(MongoDBMaintenanceJob.DBNAME, LookupService.LOOKUP_SERVICE);

            // Trigger the job to run now, and then every dbpruneInterval seconds
            Trigger trigger = newTrigger().withIdentity(LookupService.LOOKUP_SERVICE + "DBTrigger", "DBMaintenance")
                    .startNow()
                    .withSchedule(simpleSchedule()
                            .withIntervalInSeconds(dbpruneInterval)
                            .repeatForever()
                            .withMisfireHandlingInstructionIgnoreMisfires())
                    .build();

            scheduler.schedule(job, trigger);
        }

        PublishService publishService = PublishService.getInstance();
        publishService.startService();

        JobDetail gcInvoker = newJob(MemoryManager.class)
                .withIdentity("gc", "MemoryManagement")
                .build();

        Trigger gcTrigger = newTrigger().withIdentity("gc trigger", "MemoryManagement")
                .startNow()
                .withSchedule(simpleSchedule()
                        .withIntervalInSeconds(60)
                        .repeatForever()
                        .withMisfireHandlingInstructionIgnoreMisfires())
                .build();

        scheduler.schedule(gcInvoker, gcTrigger);


        // Block forever
        Object blockMe = new Object();
        synchronized (blockMe) {
            blockMe.wait();

        }


    }


    public static void parseArgs(String args[]) throws java.io.IOException {

        OptionParser parser = new OptionParser();
        parser.acceptsAll(asList("h", "?"), "show help then exit");
        OptionSpec<String> PORT = parser.accepts("p", "server port").withRequiredArg().ofType(String.class);
        OptionSpec<String> HOST = parser.accepts("h", "host").withRequiredArg().ofType(String.class);
        OptionSpec<String> CONFIG = parser.accepts("c", "configPath").withRequiredArg().ofType(String.class);
        OptionSpec<String> LOGCONFIG = parser.accepts("l", "logConfig").withRequiredArg().ofType(String.class);
        OptionSpec<String> QUEUEDATADIR = parser.accepts("q", "queueDataDir").withRequiredArg().ofType(String.class);
        OptionSpec<String> DATADIR = parser.accepts("d", "dataDir").withRequiredArg().ofType(String.class);
        OptionSet options = parser.parse(args);

        // check for help
        if (options.has("?")) {

            parser.printHelpOn(System.out);
            System.exit(0);

        }

        if (options.has(PORT)) {

            port = Integer.parseInt(options.valueOf(PORT));

        }

        if (options.has(HOST)) {

            host = options.valueOf(HOST);

        }

        if (options.has(CONFIG)) {

            configPath = options.valueOf(CONFIG);
            System.out.println("Config files Path:" + configPath);

        }

        if (options.has(LOGCONFIG)) {

            logConfig = options.valueOf(LOGCONFIG);

        }


        if (options.has(QUEUEDATADIR)) {

            queueDataDir = options.valueOf(QUEUEDATADIR);

        }

        if (options.has(DATADIR)) {

            dataDir = options.valueOf(DATADIR);

        }

    }


}
