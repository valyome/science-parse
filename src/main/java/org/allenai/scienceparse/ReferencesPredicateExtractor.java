package org.allenai.scienceparse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.allenai.ml.sequences.crf.CRFPredicateExtractor;
import com.gs.collections.api.map.primitive.ObjectDoubleMap;
import com.gs.collections.api.tuple.Pair;
import com.gs.collections.impl.map.mutable.primitive.ObjectDoubleHashMap;

import lombok.val;

public class ReferencesPredicateExtractor implements CRFPredicateExtractor<String, String> {

  private ParserLMFeatures lmFeats;
  
  public ReferencesPredicateExtractor() {
    this(null);
  }
  
  public ReferencesPredicateExtractor(ParserLMFeatures lmf) {
    lmFeats = lmf;
  }
  
  public int locationBin(int i, int total) {
    return (12 * i) / total;
  }
  
  public int addPunctuationFeatures(String tok, ObjectDoubleHashMap<String> m) {
    //TODO: use a list and loop here
    Pattern pInitialQuote = Pattern.compile("\\p{Pi}");
    Pattern pEndQuote = Pattern.compile("\\p{Pf}");
    Pattern pDoubleDash = Pattern.compile("\\p{Pd}\\p{Pd}");
    Pattern pContinuing = Pattern.compile(",;:$");
    Pattern pEnding = Pattern.compile("(\\.$|\\p{Pf}$)");
    Pattern pPairedBraces = Pattern.compile("\\p{Ps}.*\\p{Pe}");
    int ct = 0;
    if(RegexWithTimeout.matcher(pInitialQuote, tok).find()) {
      m.put("%pInitialQuote", 1.0);
      ct++;
    }
    if(RegexWithTimeout.matcher(pEndQuote, tok).find()) {
      m.put("%pEndQuote", 1.0);
      ct++;
    }
    if(RegexWithTimeout.matcher(pDoubleDash, tok).find()) {
      m.put("%pDoubleDash", 1.0);
      ct++;
    }
    if(RegexWithTimeout.matcher(pContinuing, tok).find()) {
      m.put("%pContinuing", 1.0);
      ct++;
    }
    if(RegexWithTimeout.matcher(pEnding, tok).find()) {
      m.put("%pEnding", 1.0);
      ct++;
    }
    if(RegexWithTimeout.matcher(pPairedBraces, tok).find()) {
      m.put("%pPairedBraces", 1.0);
      ct++;
    }
    return ct;
  }
  
  //borrowing from ParsCit's features
  public int addNumberFeatures(String tok, ObjectDoubleHashMap<String> m) {
    int ct = 0;
    Pattern pPageRange = Pattern.compile("[0-9]+-[0-9]+\\p{P}?");
    if(RegexWithTimeout.matcher(pPageRange, tok).matches()) {
      m.put("%pRange", 1.0);
      ct++;
    }
    Pattern pVolume = Pattern.compile("[0-9](\\([0-9]+\\))?\\p{P}?");
    if(RegexWithTimeout.matcher(pVolume, tok).matches()) {
      m.put("%pVolume", 1.0);
      ct++;
    }
    Pattern pDigits = Pattern.compile("[0-9]+\\p{P}?");
    if(RegexWithTimeout.matcher(pDigits, tok).matches()) {
      int len = Math.min(tok.length(), 5);
      String digFeat = "%digs" + len;
      m.put(digFeat, 1.0);
    }
    if(RegexWithTimeout.matcher(pDigits, tok).find()) {
      m.put("%hasDigit", 1.0);
    }
    else {
      m.put("%noDigit", 1.0);
    }
    Pattern pOrdinal = Pattern.compile("[0-9]+(st|nd|rd|th)\\p{P}?");
    if(RegexWithTimeout.matcher(pOrdinal, tok).find()) {
      m.put("%ordinal", 1.0);
    }
    return ct;
  }
  
  public static boolean containsEditor(List<String> elems) {
    for(String sOrig : elems) {
      String s = sOrig.replaceAll(",", "");
      if(s.equalsIgnoreCase("eds.")||s.equalsIgnoreCase("ed.")||s.equalsIgnoreCase("editor")||s.equalsIgnoreCase("editors"))
        return true;
    }
    return false;
  }
  
  public static boolean isMonth(String tok) {
    List<String> months = Arrays.asList("January", "February", "March", "April", "June", "July", "August",
        "September", "October", "November", "December",
        "Jan.", "Feb.", "Mar.", "Apr.", "Jun.", "Jul.", "Aug.", "Sept.", "Oct.", "Nov.", "Dec.");

    if(months.contains(tok.replaceAll(",", "")))
      return true;
    else
      return false;
  }
  
  public static boolean addTokenFeatures(String tok, ObjectDoubleHashMap<String> m) {
//    m.put("%raw=" + tok, 1.0);
//    m.put("%rawlc=" + tok.toLowerCase(), 1.0);
    m.put("%rawnopunct=" + tok.toLowerCase().replaceAll("\\p{P}", ""), 1.0);
    String prefix = tok.substring(0, Math.min(tok.length(), 4));
    String suffix = tok.substring(Math.max(tok.length() - 4, 0));
    m.put("%prefix=" + prefix, 1.0);
    m.put("%suffix=" + suffix, 1.0);
    return true;
  }
  
  @Override
  public List<ObjectDoubleMap<String>> nodePredicates(List<String> elems) {
    List<ObjectDoubleMap<String>> out = new ArrayList<>();
    boolean hasEditor = containsEditor(elems);
    for (int i = 0; i < elems.size(); i++) {
      ObjectDoubleHashMap<String> m = new ObjectDoubleHashMap<>();
      //word features:
      String tok = elems.get(i);
  
      PDFPredicateExtractor.getCaseMasks(tok).forEach(
        (String s) -> m.put(s, 1.0)); //case masks
      if (PDFPredicateExtractor.isStopWord(tok)) {
        m.put("%stop", 1.0); //stop word
        if (m.containsKey("%XXX") || m.containsKey("%Xxx"))
          m.put("%startCapStop", 1.0); //is a stop word that starts with a capital letter
      } else {
        if (m.containsKey("%xxx")) {
          m.put("%uncapns", 1.0); //is an uncapitalized stop word
        }
      }
      double adjLen = Math.min(tok.length(), 10.0) / 10.0;
      double adjLenSq = (adjLen - 0.5) * (adjLen - 0.5);
      m.put("%adjLen", adjLen); //adjusted word length
      m.put("%adjLenSq", adjLenSq); //adjusted word length squared (?)

      if (lmFeats != null) { //how well does token match title/author gazeetters
        m.put("%tfreq", PDFPredicateExtractor.smoothFreq(tok, this.lmFeats.titleBow));
        m.put("%tffreq", PDFPredicateExtractor.smoothFreq(tok, this.lmFeats.titleFirstBow));
        m.put("%tlfreq", PDFPredicateExtractor.smoothFreq(tok, this.lmFeats.titleLastBow));
        m.put("%afreq", PDFPredicateExtractor.smoothFreq(Parser.normalizeAuthor(tok), this.lmFeats.authorBow));
        m.put("%affreq", PDFPredicateExtractor.smoothFreq(Parser.normalizeAuthor(tok), this.lmFeats.authorFirstBow));
        m.put("%alfreq", PDFPredicateExtractor.smoothFreq(Parser.normalizeAuthor(tok), this.lmFeats.authorLastBow));
        m.put("%vfreq", PDFPredicateExtractor.smoothFreq(tok, this.lmFeats.venueBow));
        m.put("%vffreq", PDFPredicateExtractor.smoothFreq(tok, this.lmFeats.venueFirstBow));
        m.put("%vlfreq", PDFPredicateExtractor.smoothFreq(tok, this.lmFeats.venueLastBow));
        m.put("%bfreq", PDFPredicateExtractor.smoothFreq(tok, this.lmFeats.backgroundBow));
        m.put("%bafreq", PDFPredicateExtractor.smoothFreq(Parser.normalizeAuthor(tok), this.lmFeats.backgroundBow));
      }
      String locBinFeat = "%locbin" + locationBin(i, elems.size());
      m.put(locBinFeat, 1.0);
      addNumberFeatures(tok, m);
      if(hasEditor)
        m.put("%editor", 1.0);
      addTokenFeatures(tok, m);
      addPunctuationFeatures(tok, m);
      out.add(m);
    }
    return out;
  }

  @Override
  public List<ObjectDoubleMap<String>> edgePredicates(List<String> elems) {
    val out = new ArrayList<ObjectDoubleMap<String>>();
    for (int i = 0; i < elems.size() - 1; i++) {
      val odhm = new ObjectDoubleHashMap<String>();
      odhm.put("B", 1.0);
      out.add(odhm);
    }
    return out; //I don't really understand these things.
  }
  
}