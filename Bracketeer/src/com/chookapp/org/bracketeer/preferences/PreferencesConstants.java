package com.chookapp.org.bracketeer.preferences;

public final class PreferencesConstants
{ 
    public static final int MAX_PAIRS = 4;
    
    public final static class Surrounding
    {
        private static final String SurroundingPath = "Surrounding.";
        
        public static final String Enable = SurroundingPath + "Enable";
        public static final String ShowBrackets = SurroundingPath + "ShowBrackets";
        public static final String NumBracketsToShow = SurroundingPath + "NumBracketsToShow"; 
    }
    
    public final static class Hovering
    {
        private static final String HoveringPath = "Hovering.";
        
        public static final String Enable = HoveringPath + "Enable";
    }
    
    public final static class Highlights
    {
        private static final String HighlightsPath = "Highlights.";
        
        private static final String Default = "Default.";
        private static final String MatchingPairPrefix = "MatchingPair";
        private static final String MissingPair = "MissingPair.";
        
        private static final String Background = "Background.";
        private static final String Foreground = "Foreground.";        
        
        /**
         * 
         * @param typeId
         *  - 0 is default
         *  - 1 - MAX_PAIRS is matching pairs
         *  - MAX_PAIRS+1 is missing pair
         * @param foregound
         * @return
         */
        public static String getAttrPath(int typeId, boolean foregound)
        {
            String path = HighlightsPath;
            
            if( typeId == 0 )
                path += Default;
            else if( typeId < MAX_PAIRS + 1)
                path += MatchingPairPrefix + typeId + ".";
            else // typeId == MAX_PAIRS + 1
                path += MissingPair;
            
            if( foregound )
                path += Foreground;
            else
                path += Background;
            
            return path;
        }
        
        public static final String UseDefault = "UseDefault";
        public static final String Color = "Color";

        
    }
    
    public final static class Hints
    {
        public static final String ENABLED = "enabled";
        public static final String FG_COLOR = "fgColor";
        public static final String BG_COLOR = "bgColor";
        
        public static String preferencePath(String hintType)
        {
            return "Hints." + hintType + ".";
        }
        
        
    }
    
    public static String preferencePath(String pluginName)
    {
        return pluginName+".";
    }
}
