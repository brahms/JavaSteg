

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;

import javax.imageio.ImageIO;

public class JavaSteg
{
    static BufferedImage mImage;
    
    public static void main(String[] args)
    {
        
        try {
            if(args.length < 2) {
                help();
            }
            
            String filename = args[0];
            File file = new File(filename);
            
            log("Using file: " + file.getAbsolutePath());
            
            if(file.canRead()) {
                mImage = ImageIO.read(file);
                boolean decode = args[1].equals( "--decode");
                if(decode) {
                    log("Trying to decode.");
                    StegoImage image = new StegoImage();
                    image.setImageBufferedImage(mImage);
                    try {
                        image.decode();
                    }
                    catch(DecodingException ex) {
                        log("Not a valid steg image, can't decode.", ex);
                    }
                    if(image.hasEmbeddedData()) {
                        logAndExit("Decoded sentence: " + new String(image.getEmbeddedData(), Constants.CHARSET));
                    }
                }
                else if(args[1].equals("--debug")) {
                    int i =0;
                    for(int x = 0; x < mImage.getWidth(); x++) {
                        for(int y = 0; y < mImage.getHeight(); y++) {
                            if(i % 100 == 0){
                                log(String.format("Pixel %d, %d is %x", x, y, mImage.getRGB(x, y)));
                            }
                            i++;
                        }
                    }
                }
                else if(args[1].equals("--run-script")) {
                    log("Running script.");
                    
                    File scriptFolder = new File("out/script");
                    File outputFolder = new File(scriptFolder, file.getName());
                    
                    Utils._assert(scriptFolder.mkdirs() || scriptFolder.isDirectory());
                    Utils._assert(outputFolder.mkdirs() || outputFolder.isDirectory());
                    
                    
                    int maxBytes = StegoImage.getMaxBytesEncodable(mImage.getHeight(), mImage.getWidth());
                    log(String.format("Max bytes encodable in a bitmap %d height, %d width is %d",mImage.getHeight(), mImage.getWidth(), maxBytes));
                    for(Character c = 'A'; c <= 'Z'; c++) {
                        StringBuilder builder = new StringBuilder();
                        for(Integer i=1; i < maxBytes - 1; i++) {
                            builder.append(c);
                            
                            if(i % 2500 == 0) {
                                log("Encoding " + i + " repetitions of " + c);
                                StegoImage image = new StegoImage();
                                image.setImageBufferedImage(mImage);
                                image.setEmbeddedData(builder.toString().getBytes(Constants.CHARSET));
                                image.encode();
                                
                                File outFile = new File(outputFolder, c.toString() + i.toString() + ".png");

                                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outFile));
                                bos.write(image.getImageBytes());
                                bos.flush();
                                bos.close();
                                log(String.format("Wrote %d bytes to %s", image.getImageBytes().length, outFile));
                            }
                        }
                    }
                }
                else if(args[1].equals("--help")){
                    help();
                }
                else if(args[1].equals("--encode")){
                    if(args.length < 3 || args[2] == null) logAndExit("--encode requires a sentence.");
                    String sentence = args[2];
                    log("Trying to encode: " + sentence);
                    StegoImage image = new StegoImage();
                    image.setImageBufferedImage(mImage);
                    image.setEmbeddedData(sentence.getBytes(Constants.CHARSET));
                    image.encode();
                    File outFolder = new File("out");
                    outFolder.mkdirs();
                    
                    File outFile = new File("out", "encoded-" + file.getName() + ".png");
                    
                    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outFile));
                    bos.write(image.getImageBytes());
                    bos.flush();
                    bos.close();
                    log(String.format("Wrote %d bytes to %s", image.getImageBytes().length, outFile));
                    
                }
                    
            }
            
            else {
                logAndExit("File can't be read.");
            }
        }
        catch(Exception ex) {
            log("Error in main", ex);
        }

    }
  
    
    private static void help()
    {
        String string = 
                "JavaSteg.jar image_file [--encode sentence] | [--decode] [--run-script]\n" +
                "\n" +
                "   Written by Chris Brahms for ISA 564. \n" +
                "\n This program implements LSB for R, G, and B of every pixel value in the bitmap giving 3 bits of data per pixel.\n" +
                "   The program saves encoded files in the png format, because 1) it's a lesser waste of space and 2) java's ImageIO doesn't like writing to .bmp for some reason.\n\n" +
                "Options:\n" +
                "--encode    Encode a given sentence into the resulting bitmap file, which can be found in ./out/image_file.png \n\n" +
                "--decode    Decodes a given bitmap file and tells you the encoded sentence if any.\n\n" +
                "--run-script Runs a script output a bunch of pngs with different amonts of A's, B's, C's, ..., encoded. Files are saved to ./out/image_file/ \n\n";
        logAndExit(string);
        
    }


    private static void logAndExit(String message) {
        log(message);
        System.exit(0);
    }
    private static void log(String message) {
        System.out.println(message);
    }
    private static void log(String message, Exception ex) {
        System.out.println(message);
        System.out.println(ex.toString());
        ex.printStackTrace();
    }
    
    static class DecodingException extends Exception {
        DecodingException(String message) {
            super(message);
        }
        
    }
    static class EncodingException extends Exception {
        EncodingException(String message) {
            super(message);
        }
        
    }
    static class StegoImage {
        /**
         * This is the ratio of actual embedded bits per pixel
         */
        public static final double RATIO = 0.09375;
        
        private static final int R_FLAG = 0x00010000;
        private static final int G_FLAG = 0x00000100;
        private static final int B_FLAG = 0x00000001;
        
        private static final int STATE_R = 0;
        private static final int STATE_G = 1;
        private static final int STATE_B = 2;

        protected byte[] mImageBytes;
        protected byte[]  mEmbeddedData;
        protected BufferedImage mImageBufferedImage;
        
        protected static final int STATE_START = 0x00;
        protected static final int STATE_GOT_MAGIC = 0x01;
        protected static final int STATE_GOT_LENGTH = 0x02;
        
        public boolean hasEmbeddedData() {
            return mEmbeddedData != null;
        }
        
        public byte[] getEmbeddedData() {
            return mEmbeddedData;
        }
        
        public void setEmbeddedData(byte[] data) {
            mEmbeddedData = data;
        }
        
        public void setImageBytes(byte[] imageBytes) {
            mImageBytes = imageBytes;
        }
        
        /**
         * The actual bytes that are the image we are trying
         * to decode/encode from/into
         * @return
         */
        public byte[] getImageBytes() {
            return mImageBytes;
        }
        
        
        public void setImageBufferedImage(BufferedImage image) {
            mImageBufferedImage = image;
        }
        
        
        public BufferedImage getImageBufferedImage() 
        {
            return mImageBufferedImage;
        }

        public void decode() throws DecodingException{
            if(getImageBufferedImage() == null) {
                throw new DecodingException("Decode called without image");
            }
            
            
            BufferedImage b = getImageBufferedImage();
            boolean gotByte = true;
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            LinkedList<Boolean> bitBuffer = getBitBuffer();
            
            int currentState = STATE_START;
            int length = 0;
            double totalPixels = b.getHeight() * b.getWidth();
            int maxLength   = (int) Math.floor((totalPixels * RATIO) - Constants.STEGO_HEADER_LENGTH);
            for(int x = 0; x < b.getWidth(); x++) {
                for(int y = 0; y < b.getHeight(); y++) {
                    int pixel = b.getRGB(x, y);
                    boolean firstBit  = (pixel & R_FLAG) != 0;
                    boolean secondBit = (pixel & G_FLAG) != 0;
                    boolean thirdBit  = (pixel & B_FLAG) != 0;

                    Byte bite = null;
                    
                    bite = pushBit(firstBit, bitBuffer);
                    gotByte = handleByte(bite, outputStream, bitBuffer, gotByte);
                    
                    bite = pushBit(secondBit, bitBuffer);
                    gotByte = handleByte(bite, outputStream, bitBuffer, gotByte);
                    
                    bite = pushBit(thirdBit, bitBuffer);
                    gotByte = handleByte(bite, outputStream, bitBuffer, gotByte);

                    
                    if(gotByte) {
                        switch(currentState) {
                        case STATE_START:
                            if(outputStream.size() == Constants.MAGIC_VALUE_LENGTH) {
                                int val = 
                                        ByteBuffer
                                        .wrap(outputStream.toByteArray())
                                        .order(Constants.BYTE_ORDER)
                                        .getInt();
                                
                                if(val == Constants.MAGIC_VALUE) {
                                    currentState = STATE_GOT_MAGIC;
                                }
                                else {
                                    throw new DecodingException("Bad magic value: " + val);
                                }
                            }
                            break;
                        case STATE_GOT_MAGIC:
                            if(outputStream.size() == Constants.STEGO_HEADER_LENGTH) {
                                length = ((ByteBuffer) ByteBuffer
                                                .wrap(outputStream.toByteArray())
                                                .order(Constants.BYTE_ORDER)
                                                .position(Constants.MAGIC_VALUE_LENGTH))
                                                .getInt();
                                if(length > 0 &&
                                   length <= maxLength) {
                                    currentState = STATE_GOT_LENGTH;
                                    outputStream = new ByteArrayOutputStream(length);
                                }
                                else {
                                    throw new DecodingException(String.format("Bad length: %d, Max is: %d", length, maxLength));
                                }
                            }
                            break;
                        case STATE_GOT_LENGTH:
                            if(outputStream.size() == length){
                                setEmbeddedData(outputStream.toByteArray());
                                return;
                            }
                            break;
                        }
                    }
                }
            }

            //
            // if we got here, theres an exception.
            //
            
            throw new DecodingException("Never retrieved enough bytes.");
        }

        private boolean handleByte(Byte bite, ByteArrayOutputStream outputStream,
                List<Boolean> bitBuffer, boolean gotByte) {
            if(bite != null) {
                outputStream.write(bite);
                bitBuffer.clear();
                return true;
            }
            else {
                return gotByte;
            }
        }
        public void encode() throws EncodingException, IOException {

            if(getImageBufferedImage() == null) {
                throw new EncodingException("Encode called without image BufferedImage.");
            }
            if(getEmbeddedData() == null) {
                throw new EncodingException("Encode called without embeded data.");
            }
            Utils._assert(getEmbeddedData().length < StegoImage.getMaxBytesEncodable(getImageBufferedImage()));
            
            List<Boolean> bits = getBitsForData(getEmbeddedData());
            BufferedImage mutableBufferedImage = new BufferedImage(getImageBufferedImage().getWidth(), getImageBufferedImage().getHeight(), BufferedImage.TYPE_INT_ARGB);
            log(String.format("The bitmaps height is %d, and width is %d: ", mutableBufferedImage.getHeight(), mutableBufferedImage.getWidth()));
            /*
             * Here's our stego algo.
             * 
             * Given our bit array, we set each pixels RGB values
             * individually even or odd depending if the value is odd or even
             * 
             * Set = odd,
             * Not set = even
             * 
             * We get 3 bits per pixel.
             */
            int x = 0;
            int y = 0;
            int currentPixel = -1;
            int currentState = STATE_R;
            for(boolean bit : bits) {
                boolean isNonBlackOrWhite = true;
                switch(currentState) {
                
                case STATE_R:
                    if(x >= getImageBufferedImage().getWidth()) {
                        throw new EncodingException("Too many bytes given, current x is: " + x);
                    }
                    
                    currentPixel = getImageBufferedImage().getRGB(x, y);
                    isNonBlackOrWhite = (currentPixel != 0xFF000000 &&  currentPixel != 0xFFFFFFFF);
                    // remove alpha via mask, then add an 0xFF alpha back in to prevent any
                    // alpha multiplication rounding errors.
                    currentPixel &= 0x00FFFFFF;
                    currentPixel += 0xFF000000;
                   
                    currentPixel = setByte(currentPixel, R_FLAG, bit);
                    
                    currentState = STATE_G;
                    break;
                case STATE_G:
                    currentPixel = setByte(currentPixel, G_FLAG, bit);
                    currentState = STATE_B;
                    break;
                case STATE_B:
                    currentPixel = setByte(currentPixel, B_FLAG, bit);
                    if(isNonBlackOrWhite == false) log(String.format("Set pixel %d, %d from %x to  %x", x, y, mutableBufferedImage.getRGB(x,y), currentPixel));
                    mutableBufferedImage.setRGB(x, y, currentPixel);

                    Utils._assert(mutableBufferedImage.getRGB(x, y) == currentPixel);
                    y++;
                    if(y >= mutableBufferedImage.getHeight()) {
                        y = 0;
                        x++;
                    }
                    currentState = STATE_R;
                    break;
                }
            }
            
            //
            // Handle the case where we have only set 2 or 1 bits in a given pixel
            // before we reach the end of our data.
            //
            if(currentState != STATE_R) {
                mutableBufferedImage.setRGB(x, y++, currentPixel);
            }
            for(; x < getImageBufferedImage().getWidth(); x++){
                for(; y< getImageBufferedImage().getHeight(); y++) {
                    mutableBufferedImage.setRGB(x, y, mImageBufferedImage.getRGB(x, y));
                }
                y = 0;
            }
            
            //
            // Encode our image into a set of bytes.
            //
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(mutableBufferedImage, "png", bos);
            bos.flush();
            bos.close();
            setImageBytes(bos.toByteArray());
        }
        
        public int setByte(int pixel, int flag, boolean bit) {
            if(bit) {
                return setByteOdd(pixel, flag);
            }
            else {
                return setByteEven(pixel, flag);
            }
        }
        
        public int setByteOdd(int pixel, int flag) {
            pixel = zeroBit(pixel, flag);
            return pixel ^ flag;
        }
        
        private int zeroBit(int pixel, int flag) {
            return pixel & (0xFFFFFFFF ^ flag);
        }

        public int setByteEven(int pixel, int flag) {
            return zeroBit(pixel, flag);
        }
        public static int getMaxBytesEncodable(int height, int width) {
            int bytes =  (int) Math.floor(RATIO * (double) (height * width)) - Constants.STEGO_HEADER_LENGTH;

            log(String.format("Max bytes for height of %d pixels and width of %d pixels is: %d", height, width, bytes));
            
            return bytes;
        }
        public static int getMaxBytesEncodable(BufferedImage mCoverImage) {
            return getMaxBytesEncodable(mCoverImage.getHeight(), mCoverImage.getWidth());
        }

        
        public static LinkedList<Boolean> getBitsForData(byte[] embeddedData) {
            Utils._assert(embeddedData.length <= Integer.MAX_VALUE);
            ByteBuffer b = ByteBuffer
                    .allocate(Constants.STEGO_HEADER_LENGTH)
                    .order(Constants.BYTE_ORDER)
                    .putInt(Constants.MAGIC_VALUE)
                    .putInt(embeddedData.length);
            
            b.clear();
            
            byte[] header = new byte[Constants.STEGO_HEADER_LENGTH];
            b.get(header);
            
            LinkedList<Boolean> bits = getBitBuffer();
            
            pushBytes(header, bits);
            pushBytes(embeddedData, bits);
            
            return bits;
            
        }
        
        public static void pushBytes(byte[] bytes, LinkedList<Boolean> bits) {
            for(byte bite : bytes) {
                pushByte(bite, bits);
            }
        }
        
        public static void pushByte(byte bite, LinkedList<Boolean> bits) {
            for(int i = 7; i >= 0; i--) {
                byte flag = (byte) (0x01 << i);
                
                boolean bit = (bite & flag) != 0;
                bits.addLast(bit);
            }
        }

        public static LinkedList<Boolean> getBitBuffer()
        {
            return new LinkedList<Boolean>();
        }
        
        public static Byte pushBit(boolean nextBit, LinkedList<Boolean> bitBuffer) 
        {
            Utils._assert(bitBuffer != null);
            Utils._assert(bitBuffer.size() < 8);
            
            bitBuffer.addLast(nextBit);
            
            if(bitBuffer.size() == 8) 
            {
                return getByteFromBitBuffer(bitBuffer);
            }
            
            return null;
        }
        
        public static Byte getByteFromBitBuffer(LinkedList<Boolean> bits) {
            if(bits.size() < 8) {
                return null;
            }
            
            byte bite = (byte) 0xFF;
            
            if(!bits.pop()) {
                bite ^= Constants.EIGTH_BIT;
            }
            if(!bits.pop()) {
                bite ^= Constants.SEVENTH_BIT;
            }
            if(!bits.pop()) {
                bite ^= Constants.SIXTH_BIT;
            }
            if(!bits.pop()) {
                bite ^= Constants.FIFTH_BIT;
            }
            if(!bits.pop()) {
                bite ^= Constants.FOURTH_BIT;
            }
            if(!bits.pop()) {
                bite ^= Constants.THIRD_BIT;
            }
            if(!bits.pop()) {
                bite ^= Constants.SECOND_BIT;
            }
            if(!bits.pop()) {
                bite ^= Constants.FIRST_BIT;
            }
            
            return bite;
            
        }
    }
    
    public static class Utils {
        public static void _assert(boolean b) {
            try {
                if(!b) {
                    throw new Exception("Failed assert.");
                }
                
            }
            catch(Exception ex) {
                ex.printStackTrace();
                logAndExit("");
            }
        }
    }
    public interface Constants {

        public static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;
        public static final int MAGIC_VALUE = 0xDEADBEEF;
        public static final int MAGIC_VALUE_LENGTH = 4; // bytes
        public static final int LENGTH_VALUE_LENGTH = 4; //bytes
        public static final int STEGO_HEADER_LENGTH = MAGIC_VALUE_LENGTH + LENGTH_VALUE_LENGTH;
        
        public static final int FIRST_BIT =  0x1 << 0;
        public static final int SECOND_BIT = 0x1 << 1;
        public static final int THIRD_BIT = 0x1 << 2;
        public static final int FOURTH_BIT = 0x1 << 3;
        public static final int FIFTH_BIT = 0x01 << 4;
        public static final int SIXTH_BIT = 0x01 << 5;
        public static final int SEVENTH_BIT = 0x01 << 6;
        public static final int EIGTH_BIT = 0x01 << 7;
        public static final int PIXEL_BLACK_WITH_ALPHA = 0xFF000000;
        public static final Charset CHARSET = Charset.forName("UTF-8");
        
    }   
    
}
