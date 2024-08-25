namespace BhanuAuthenticator
{
    using OtpNet;
    
    public class OTPGenerator
    {
        private readonly byte[] _secretKey;

        public OTPGenerator(string base32Secret)
        {
            _secretKey = Base32Encoding.ToBytes(base32Secret);
        }

        public string GenerateOtp()
        {
            var totp = new Totp(_secretKey);
            return totp.ComputeTotp();
        }

        public bool VerifyOtp(string otp)
        {
            var totp = new Totp(_secretKey);
            return totp.VerifyTotp(otp, out long timeStepMatched);
        }
    }
}