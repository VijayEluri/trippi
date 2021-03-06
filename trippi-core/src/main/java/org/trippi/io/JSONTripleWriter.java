package org.trippi.io;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Iterator;
import java.util.Map;

import org.jrdf.graph.Literal;
import org.jrdf.graph.Node;
import org.jrdf.graph.ObjectNode;
import org.jrdf.graph.Triple;
import org.jrdf.graph.URIReference;
import org.trippi.Alias;
import org.trippi.AliasManager;
import org.trippi.RDFUtil;
import org.trippi.TrippiException;
import org.trippi.TripleIterator;
import org.trippi.impl.base.DefaultAliasManager;

/**
 * Writes triples as JSON.
 *
 * The N2/Talis spec: http://n2.talis.com/wiki/RDF_JSON_Specification
 * The W3C SPARQL/JSON spec: http://www.w3.org/TR/rdf-sparql-json-res/
 *
 * UTF-8 encoding is used for extended characters.
 */
public class JSONTripleWriter extends TripleWriter {

    private PrintWriter m_out;
    private AliasManager m_aliases;

    private static Writer defaultWriter(OutputStream out) throws TrippiException {
        try {
            return new OutputStreamWriter(out, "UTF-8");
        } catch (IOException e) {
            throw new TrippiException("Error setting up writer", e);
        }
    }
    
    public JSONTripleWriter(OutputStream out, Map<String, String> aliases) throws TrippiException {
        this(out, new DefaultAliasManager(aliases));
    }

    public JSONTripleWriter(OutputStream out, AliasManager aliases) throws TrippiException {
        this(defaultWriter(out), aliases);
    }

    public JSONTripleWriter(Writer out, Map<String, String> aliases) throws TrippiException {
        this(out, new DefaultAliasManager(aliases));
    }

    public JSONTripleWriter(Writer out, AliasManager aliases) throws TrippiException {
        m_out = new PrintWriter(out);
        m_aliases = aliases;
    }

    public int write(TripleIterator iter) throws TrippiException {

            m_out.println("{\"results\":[");
            int count = 0;
            while (iter.hasNext()) {
            	if (count > 0) m_out.print(',');
            	Triple result = iter.next();
                m_out.println(toJSON(result));
                count++;
            }
            m_out.print("]}");

            m_out.flush();
            iter.close();
            return count;
    }
    
    private final static String JSON_TEMPLATE = "{\"%\" : {\"%\" : [ % ] } }";
    
    /**
     * http://n2.talis.com/wiki/RDF_JSON_Specification
     * http://docs.api.talis.com/platform-api/output-types/rdf-json
     * @param triple
     * @return JSON serialization of the triple
     */
    public static String toJSON(Triple triple) {
    	String [] parts = JSON_TEMPLATE.split("%");
    	String subject = triple.getSubject().stringValue();
    	String predicate = triple.getPredicate().stringValue();
    	String object = toJSON(triple.getObject());
    	StringBuffer result = new StringBuffer((JSON_TEMPLATE.length() - 3) + subject.length() + predicate.length() + object.length());
    	result.append(parts[0]);
    	result.append(subject);
    	result.append(parts[1]);
    	result.append(predicate);
    	result.append(parts[2]);
    	result.append(object);
    	result.append(parts[3]);
    	return result.toString();
    }
    
    private static String toJSON(ObjectNode node){
    	StringBuffer result = new StringBuffer();
    	result.append('{');
    	result.append("\"type\":");
    	if (node.isLiteral()){
    		result.append("\"literal\"");
    	}
    	else if(node.isURIReference()){
    		result.append("\"uri\"");
    	}
    	else {
    		result.append("\"bnode\"");
    	}
    	result.append(", \"value\":");
    	result.append('"');
    	result.append(node.stringValue().replaceAll("\"", "\\\\\""));
    	result.append('"');
    	if(node.isLiteral()){
        	result.append(", \"datatype\":");
        	result.append('"');
    		((Literal)node).getDatatypeURI();
        	result.append('"');
    	}
    	result.append('}');
    	return result.toString();
    }

    public String getValue(Node node) {
        String fullString = RDFUtil.toString(node);
        if (m_aliases != null) {
            if (node instanceof URIReference) {
                Iterator<Alias> iter = m_aliases.getAliases().values().iterator();
                while (iter.hasNext()) {
                    Alias alias = iter.next();
                    String prefix = alias.getKey();
                    String expansion = alias.getExpansion();
                    if (fullString.startsWith("<".concat(expansion))) {
                        return fix("<" + prefix + ":" + fullString.substring(expansion.length() + 1));
                    }
                }
            } else if (node instanceof Literal) {
                Literal literal = (Literal) node;
                if (literal.getDatatypeURI() != null) {
                    String uri = literal.getDatatypeURI().toString();
                    Iterator<Alias> iter = m_aliases.getAliases().values().iterator();
                    while (iter.hasNext()) {
                        Alias a = iter.next();
                        String alias = a.getKey();
                        String expansion = a.getExpansion();
                        if (uri.startsWith(expansion)) {
                            StringBuffer out = new StringBuffer();
                            out.append('"');
                            out.append(literal.getLexicalForm().replaceAll("\"", "\\\""));
                            out.append("\"^^");
                            out.append(alias);
                            out.append(':');
                            out.append(uri.substring(expansion.length()));
                            return fix(out.toString());
                        }
                    }
                }
            }
        }
        return fix(fullString);
    }

    private String fix(String in) {
        if (in.charAt(0) == '"') {
            // literal
            String lit = in.substring(1, in.lastIndexOf('"'));
            return lit.replaceAll("\\\\\"", "\"").replaceAll("\n", " ");
        } else if (in.charAt(0) == '<') {
            // resource
            return in.substring(1, in.length() - 1);
        } else {
            return in;
        }
    }

}
