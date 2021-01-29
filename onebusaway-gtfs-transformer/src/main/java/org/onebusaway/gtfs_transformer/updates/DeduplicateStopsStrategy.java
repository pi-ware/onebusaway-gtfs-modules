/**
 * Copyright (C) 2011 Brian Ferris <bdferris@onebusaway.org>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */



package org.onebusaway.gtfs_transformer.updates;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.onebusaway.collections.FactoryMap;
import org.onebusaway.collections.MappingLibrary;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.services.GtfsMutableRelationalDao;
import org.onebusaway.gtfs_transformer.services.EntityTransformStrategy;
import org.onebusaway.gtfs_transformer.services.GtfsTransformStrategy;
import org.onebusaway.gtfs_transformer.services.TransformContext;

public class DeduplicateStopsStrategy implements
        EntityTransformStrategy {

    @Override
    public void run(TransformContext context, GtfsMutableRelationalDao dao,
                    Object entity) {

        Map<AgencyAndId, List<Stop>> stopsById = new FactoryMap<AgencyAndId, List<Stop>>(
                new ArrayList<Stop>());

        for (Stop stop : dao.getAllStops()) {

            AgencyAndId aid = stop.getId();
            String id = aid.getId();

            int index = id.indexOf('_');
            if (index == -1)
                continue;

            String stopCode = id.substring(0, index);
            AgencyAndId generalId = new AgencyAndId(aid.getAgencyId(), stopCode);
            stopsById.get(generalId).add(stop);
        }

        Map<Stop, List<StopTime>> stopTimesByStop = MappingLibrary.mapToValueList(
                dao.getAllStopTimes(), "stop", Stop.class);

        for (Map.Entry<AgencyAndId, List<Stop>> entry : stopsById.entrySet()) {

            AgencyAndId stopId = entry.getKey();
            List<Stop> stops = entry.getValue();

            // Remove the stop with the old id
            Stop stop = stops.get(0);
            dao.removeEntity(stop);

            // Add the stop with new id
            stop.setId(stopId);
            dao.saveEntity(stop);

            for (int i = 1; i < stops.size(); i++) {
                Stop duplicateStop = stops.get(i);
                dao.removeEntity(duplicateStop);
                List<StopTime> stopTimes = stopTimesByStop.get(duplicateStop);
                if (stopTimes == null)
                    continue;
                for (StopTime stopTime : stopTimes)
                    stopTime.setStop(stop);
            }
        }
    }
}
