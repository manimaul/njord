package io.madrona.njord

import org.junit.Assert.*
import org.junit.Test

class NmeaChecksumTest {

    private val subject = NmeaChecksum()

    @Test
    fun testCreateVendorMessage() {
        val msgSentence = subject.createVendorMessage("foo bar")
        assertTrue(subject.isValid(msgSentence))
        assertEquals("\$PNJM,foo bar*02", msgSentence)
    }

    @Test
    fun testCreateVendorMessageFiltered() {
        val msgSentence = subject.createVendorMessage("foo bar\uD83D\uDE00", filter = true)
        assertTrue(subject.isValid(msgSentence))
        assertEquals("\$PNJM,foo bar*02", msgSentence)
    }

    @Test
    fun testCreateVendorMessageUnFiltered() {
        val msgSentence = subject.createVendorMessage("foo bar\uD83D\uDE00", filter = false)
        assertFalse(subject.isValid(msgSentence))
        assertNull(msgSentence)
    }

    @Test
    fun isValid() {
        val valid = listOf(
                "\$GPHDT,142.56,T*01",
                "!AIVDM,1,1,,A,B5NO2a0005kv8P6hdaB=owp5WP06,0*0B",
                "\$GPHDT,143.93,T*09",
                "\$GPGGA,214540.00,4716.79378,N,12224.01536,W,2,10,0.8,3.2,M,-18.7,M,7.0,0131*7A",
                "!AIVDM,1,1,,B,ENkb9Ta8:SRb@9WbW2@:9V@9Sh@;Wdo;=`e>800003vP000,2*31",
                "!AIVDM,1,1,,A,15Mi>JP000G?S3PK2QAh0rs@0<0b,0*74",
                "\$GPRMC,214540.00,A,4716.79378,N,12224.01536,W,0.06,356.11,250819,15.2,E,D*13",
                "!AIVDM,1,1,,B,35NBpm0PASo?GwfK5iP;vb7@21rP,0*78",
                "!AIVDM,1,1,,A,ENkb9MI8:SRb@9WbW2@:9V@:0h@;Wma9=Tjvh00003vP000,2*45",
                "!AIVDM,1,1,,A,403Owpiv><me`o=shpK=Bfg020S:,0*7B",
                "!AIVDM,1,1,,B,403Owpiv><me`o=shpK=Bfg020S:,0*78",
                "!AIVDM,1,1,,A,403OwpAv><me`o?l2lK7Hv?028Gv,0*10",
                "!AIVDM,1,1,,A,403OwpQv><me`G?:GDK4LLg02D1o,0*55",
                "!AIVDM,1,1,,B,403OwpAv><me`o?l2lK7Hv?028H0,0*5A",
                "!AIVDM,1,1,,B,403OwpQv><me`G?:GDK4LLg02D1o,0*56",
                "!AIVDM,1,1,,A,403Ovjiv><me`o?upvK>grw02<4w,0*7E",
                "\$GPHDT,143.94,T*0E"
        )
        valid.forEach {
            assertTrue("expected sentence <$it> to be valid", subject.isValid(it))
        }
    }

    @Test
    fun isInValid() {
        val valid = listOf(
                "",
                null,
                " ",
                "!",
                "\$",
                "\$GP",
                "\$GPGGA,,,,*",
                "\$GPGGA,,,,*00"
        )
        valid.forEach {
            assertFalse("expected sentence <$it> to NOT be valid", subject.isValid(it))
        }
    }
}