package com.example.nagivator.data.repository

import com.example.nagivator.domain.model.TrafficIncidentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class NdwTrafficTest {

    @Test
    fun parsesNdwDatexV3ClosuresAccidentsRoadworksAndIgnoresSpeedManagement() {
        val sampleXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <mc:messageContainer xmlns:sit="http://datex2.eu/schema/3/situation"
                xmlns:mc="http://datex2.eu/schema/3/messageContainer"
                xmlns:loc="http://datex2.eu/schema/3/locationReferencing"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
              <mc:payload xsi:type="sit:SituationPublication">
                <sit:situation id="S1">
                  <sit:situationRecord xsi:type="sit:RoadOrCarriagewayOrLaneManagement" id="NDW_CL_1">
                    <sit:cause><sit:causeType>roadMaintenance</sit:causeType></sit:cause>
                    <sit:locationReference>
                      <loc:gmlLineString><loc:posList>51.814385 4.609521 51.81435 4.610235</loc:posList></loc:gmlLineString>
                    </sit:locationReference>
                    <sit:roadOrCarriagewayOrLaneManagementType>carriagewayClosures</sit:roadOrCarriagewayOrLaneManagementType>
                  </sit:situationRecord>
                </sit:situation>
                <sit:situation id="S2">
                  <sit:situationRecord xsi:type="sit:Accident" id="NDW_ACC_2">
                    <sit:locationReference>
                      <loc:latitude>51.9851</loc:latitude>
                      <loc:longitude>5.8987</loc:longitude>
                      <loc:roadNumber>A12</loc:roadNumber>
                    </sit:locationReference>
                  </sit:situationRecord>
                </sit:situation>
                <sit:situation id="S3">
                  <sit:situationRecord xsi:type="sit:SpeedManagement" id="NDW_SPD_3">
                    <sit:locationReference>
                      <loc:gmlLineString><loc:posList>52.0000 5.9000</loc:posList></loc:gmlLineString>
                    </sit:locationReference>
                  </sit:situationRecord>
                </sit:situation>
              </mc:payload>
            </mc:messageContainer>
        """.trimIndent()

        val incidents = TrafficRepository.parseNdwDatexXml(ByteArrayInputStream(sampleXml.toByteArray()))
        assertEquals(2, incidents.size)

        val closure = incidents[0]
        assertEquals("ndw_NDW_CL_1", closure.id)
        assertEquals(TrafficIncidentType.CLOSURE, closure.type)
        assertTrue(closure.isBlocked)
        assertEquals(51.814385, closure.latitude, 0.0001)
        assertEquals(4.609521, closure.longitude, 0.0001)
        assertEquals("NDW (NL)", closure.source)

        val acc = incidents[1]
        assertEquals("ndw_NDW_ACC_2", acc.id)
        assertEquals(TrafficIncidentType.WARNING, acc.type)
        assertFalse(acc.isBlocked)
        assertEquals("A12", acc.roadId)
        assertEquals("Unfall (NL)", acc.title)
    }
}
