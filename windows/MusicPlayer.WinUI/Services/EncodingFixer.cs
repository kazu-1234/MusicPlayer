using System.Text;

namespace MusicPlayer.WinUI.Services;

public static class EncodingFixer
{
    private const string MojibakeChars = "‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ¡¢£¤¥¦§¨©ª«¬®¯°±²³´µ¶·¸¹º»¼½¾¿ÃÅÆÇÈÉÊËÌÍÎÏ";

    public static bool IsGarbled(string? value)
    {
        if (string.IsNullOrEmpty(value)) return false;
        if (value.Contains('\uFFFD')) return true;

        foreach (var c in value)
        {
            if ((c < 32 && c is not ('\t' or '\n' or '\r')) || c == 0x7F || (c >= 0x80 && c <= 0x9F))
            {
                return true;
            }
        }

        var hasJapanese = value.Any(c => c is >= '\u3040' and <= '\u30FF' or >= '\u4E00' and <= '\u9FFF');
        if (hasJapanese) return false;

        var mojibakeCount = value.Count(c => MojibakeChars.Contains(c));
        return value.Length > 0 && (float)mojibakeCount / value.Length > 0.3f;
    }

    public static string? FixLatin1ToShiftJis(string? original)
    {
        if (string.IsNullOrEmpty(original)) return original;

        try
        {
            var latin1 = Encoding.GetEncoding("ISO-8859-1");
            var shiftJis = Encoding.GetEncoding("shift_jis");
            var bytes = latin1.GetBytes(original);
            var converted = shiftJis.GetString(bytes);

            var originalJp = original.Count(c => c is >= '\u3040' and <= '\u30FF' or >= '\u4E00' and <= '\u9FFF');
            var convertedJp = converted.Count(c => c is >= '\u3040' and <= '\u30FF' or >= '\u4E00' and <= '\u9FFF');

            return convertedJp > originalJp ? converted : original;
        }
        catch
        {
            return original;
        }
    }
}
