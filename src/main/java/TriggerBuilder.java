import org.quartz.*;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.time.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.quartz.TriggerBuilder.newTrigger;

/**
 * Created by strandquistg on 18 May, 2021
 */
public class TriggerBuilder {

    /**
     * @param jobName refers to which camera, left or right
     * @param startTime is Date object that Trigger should fire at
     * @return single Triggers, will startAt default time per agreed-upon schedule
     */
    public Trigger buildSingleTrigger(ZonedDateTime startTime, String jobName, JobKey jk){
        String tname = jobName + "_" + startTime;
        String gname = jobName + "_record_trigger";
        Trigger singleTrigger = (SimpleTrigger) newTrigger()
                .withIdentity(tname, gname)
                .startAt(Date.from(startTime.toInstant()))
                .forJob(jk)
                .build();
        System.out.println("From buildSingleTrigger, built trigger scheduled for: " + singleTrigger.getStartTime());

        return singleTrigger;
    }

    /**
     * @return list of Date objects to determine
     * which hour of each day we want cameras to start recording
     */
    public ArrayList<ZonedDateTime> setTriggerRecordingTimes(ZoneId pstZone){
        ArrayList<ZonedDateTime> recordDateTimes = new ArrayList<>();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("recordTimes.yml");
        Yaml yaml = new Yaml();
        Map<String, LinkedHashMap> configTimes = yaml.load(inputStream);

        configTimes.forEach((seshID, nestedMap) -> {
            Integer h = (Integer) nestedMap.get("hour");
            Integer m = (Integer) nestedMap.get("minute");
            Integer s = (Integer) nestedMap.get("second");
            Integer ms = (Integer) nestedMap.get("millisecond");
            LocalDateTime seshtime = LocalDateTime.of(LocalDate.now(), LocalTime.of(h, m, s, ms * 1000000));
            System.out.println("in default time builder, LocalDateTime seshtime: " + seshtime);
            ZonedDateTime zonedDateTime = seshtime.atZone(pstZone);
            System.out.println("in default time builder, ZonedDateTime zonedDateTime: " + seshtime);
            recordDateTimes.add(zonedDateTime);
        });
        return recordDateTimes;
    }

    public ZonedDateTime getNextDayFirstRecordTime(ZoneId pstZone){
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("recordTimes.yml");
        Yaml yaml = new Yaml();
        Map<String, LinkedHashMap> configTimes = yaml.load(inputStream);
        Integer firstHour = (Integer) configTimes.get("session1").get("hour");
        Integer firstMinute = (Integer) configTimes.get("session1").get("minute");
        Integer firstSecond = (Integer) configTimes.get("session1").get("second");
        LocalDateTime seshtime = LocalDateTime.of(LocalDate.now(), LocalTime.of(firstHour, firstMinute, firstSecond));
        ZonedDateTime zonedDateTime = seshtime.atZone(pstZone);
        return zonedDateTime.plusDays(1);
    }

}
