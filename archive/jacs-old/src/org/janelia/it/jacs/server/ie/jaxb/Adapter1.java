//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.1.5-b01-fcs 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2014.08.21 at 02:53:48 PM EDT 
//


package org.janelia.it.jacs.server.ie.jaxb;

import java.util.Date;
import javax.xml.bind.annotation.adapters.XmlAdapter;

public class Adapter1
    extends XmlAdapter<String, Date>
{


    public Date unmarshal(String value) {
        return (org.janelia.it.jacs.shared.utils.DateFormatter.parseDate(value));
    }

    public String marshal(Date value) {
        return (org.janelia.it.jacs.shared.utils.DateFormatter.printDate(value));
    }

}
