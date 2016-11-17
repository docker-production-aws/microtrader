package com.pluralsight.dockerproductionaws.admin;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.flywaydb.core.Flyway;

/**
 * Created by jmenga on 17/09/16.
 */
public class Migrate {
    public static void main(String[] args) {
        Config config = ConfigFactory.load();

        Flyway flyway = new Flyway();
        flyway.setBaselineOnMigrate(true);
        flyway.setLocations(config.getString("migrations.location"));
        flyway.setDataSource(
                config.getString("jdbc.url"), 
                config.getString("jdbc.user"), 
                config.getString("jdbc.password"));
        flyway.migrate();
    }
}
