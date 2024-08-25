namespace BhanuAuthenticator
{
    using System;
    using System.Timers;
    
    public partial class MainPage
    {
        private Timer _timer;
        private int _remainingTime = 10;
        private string _currentOTP;
        private static Random random = new();
        
        private void CopyAuthcode_Clicked(object sender , EventArgs e)
        {
            Clipboard.SetTextAsync(_currentOTP.Replace(" " , " "));
        }
        
        private string GenerateOTP()
        {
            var otp = random.Next(100000 , 999999).ToString("D6");
            return $"{otp.Substring(0 , 2)} {otp.Substring(2 , 2)} {otp.Substring(4 , 2)}";
        }
        
        public MainPage()
        {
            InitializeComponent();
            SetStatusBarColor();
            StartOTPGeneration();
        }
        
        private void OnTimerTick(object sender , ElapsedEventArgs e)
        {
            _remainingTime--;

            if(_remainingTime <= 0)
            {
                _remainingTime = 10;
                _currentOTP = GenerateOTP();
            }

            MainThread.BeginInvokeOnMainThread(() =>
            {
                UpdateUI();
            });
        }
        
        private void SetStatusBarColor()
        {
            #if ANDROID
                
                // Only apply this on Android
                MainThread.BeginInvokeOnMainThread(() =>
                {
                    var window = Platform.CurrentActivity?.Window;

                    if(window != null)
                    {
                        window.SetStatusBarColor(Android.Graphics.Color.Blue); // Set your desired color here
                    }
                });
                
            #endif
        }

        private void StartOTPGeneration()
        {
            _currentOTP = GenerateOTP();
            UpdateUI();

            _timer = new Timer(1000); // Timer interval set to 1 second
            _timer.Elapsed += OnTimerTick;
            _timer.AutoReset = true;
            _timer.Start();
        }

        private void UpdateUI()
        {
            OtpLabel.Text = _currentOTP;
            CountdownLabel.Text = $"{_remainingTime} second(s)";
        }
    }
}