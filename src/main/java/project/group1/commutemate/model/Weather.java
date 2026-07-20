package project.group1.commutemate.model;


/** A snapshot of current weather conditions on Burnaby Mountain. */
public record Weather(
        double temperature,     
        String condition,      
        double windSpeed,        
        String windDirection) {  
}
